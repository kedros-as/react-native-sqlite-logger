//
//  FMDBLogger.m
//  SQLiteLogger
//

#import "FMDBLogger.h"
#import "FMDatabase.h"

@interface FMDBLogger ()
- (void)validateLogDirectory;
- (void)openDatabase;
@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@interface FMDBLogEntry : NSObject {
@public
    NSNumber * level;
    NSString * message;
    NSDate   * timestamp;
}

- (id)initWithLogMessage:(DDLogMessage *)logMessage;

@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@implementation FMDBLogEntry

- (id)initWithLogMessage:(DDLogMessage *)logMessage
{
    if ((self = [super init]))
    {
        level     = @(logMessage->_flag);
        message   = logMessage->_message;
        timestamp = logMessage->_timestamp;
    }
    return self;
}

@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@implementation FMDBLogger

- (id)initWithLogDirectory:(NSString *)logFileDir logFileName:(NSNumber*)logFileName;
{
    if ((self = [super init]))
    {
        _logDirectory = [logFileDir copy];
        _logFileName = [logFileName copy];

        pendingLogEntries = [[NSMutableArray alloc] initWithCapacity:_saveThreshold];

        [self validateLogDirectory];
        [self openDatabase];
    }

    return self;
}


- (void)validateLogDirectory
{
    // Validate log directory exists or create the directory.

    BOOL isDirectory;
    if ([[NSFileManager defaultManager] fileExistsAtPath:_logDirectory isDirectory:&isDirectory])
    {
        if (!isDirectory)
        {
            NSLog(@"%@: %@ - logDirectory(%@) is a file!", [self class], THIS_METHOD, _logDirectory);
            _logDirectory = nil;
            _logFileName = nil;
        }
    }
    else
    {
        NSError *error = nil;

        BOOL result = [[NSFileManager defaultManager] createDirectoryAtPath:_logDirectory
                                                withIntermediateDirectories:YES
                                                                 attributes:nil
                                                                      error:&error];
        if (!result)
        {
            NSLog(@"%@: %@ - Unable to create logDirectory(%@) due to error: %@",
                  [self class], THIS_METHOD, _logDirectory, error);

            _logDirectory = nil;
            _logFileName = nil;
        }
    }
}

- (NSString *)getDbFilePath
{
    if (_logDirectory == nil || _logFileName == nil) {
      return nil;
    }
    return [_logDirectory stringByAppendingPathComponent:_logFileName];
}

- (void)openDatabase
{
    if (_logDirectory == nil)
    {
        return;
    }

    NSString *path = [self getDbFilePath];

    database = [[FMDatabase alloc] initWithPath:path];

    if (![database open])
    {
        NSLog(@"%@: Failed opening database!", [self class]);

        database = nil;

        return;
    }

    NSString *cmd1 = @"CREATE TABLE IF NOT EXISTS logs (log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                                                       "timestamp INTEGER, "
                                                       "level TINYINT, "
                                                       "message TEXT)";

    [database executeUpdate:cmd1];
    if ([database hadError])
    {
        NSLog(@"%@: Error creating table: code(%d): %@",
              [self class], [database lastErrorCode], [database lastErrorMessage]);

        database = nil;
    }

    NSString *cmd2 = @"CREATE INDEX IF NOT EXISTS i_log_timestamp ON logs (timestamp)";

    [database executeUpdate:cmd2];
    if ([database hadError])
    {
        NSLog(@"%@: Error creating index: code(%d): %@",
              [self class], [database lastErrorCode], [database lastErrorMessage]);

        database = nil;
    }

    [database setShouldCacheStatements:YES];
}

#pragma mark AbstractDatabaseLogger Overrides

- (BOOL)db_log:(DDLogMessage *)logMessage
{
    // You may be wondering, how come we don't just do the insert here and be done with it?
    // Is the buffering really needed?
    //
    // From the SQLite FAQ:
    //
    // (19) INSERT is really slow - I can only do few dozen INSERTs per second
    //
    // Actually, SQLite will easily do 50,000 or more INSERT statements per second on an average desktop computer.
    // But it will only do a few dozen transactions per second. Transaction speed is limited by the rotational
    // speed of your disk drive. A transaction normally requires two complete rotations of the disk platter, which
    // on a 7200RPM disk drive limits you to about 60 transactions per second.
    //
    // Transaction speed is limited by disk drive speed because (by default) SQLite actually waits until the data
    // really is safely stored on the disk surface before the transaction is complete. That way, if you suddenly
    // lose power or if your OS crashes, your data is still safe. For details, read about atomic commit in SQLite.
    //
    // By default, each INSERT statement is its own transaction. But if you surround multiple INSERT statements
    // with BEGIN...COMMIT then all the inserts are grouped into a single transaction. The time needed to commit
    // the transaction is amortized over all the enclosed insert statements and so the time per insert statement
    // is greatly reduced.

    if (logMessage && [logMessage->_message length] != 0) {
        FMDBLogEntry *logEntry = [[FMDBLogEntry alloc] initWithLogMessage:logMessage];
        @synchronized(pendingLogEntries) {
            [pendingLogEntries addObject:logEntry];
        }
    }

    // Return YES if an item was added to the buffer.
    // Return NO if the logMessage was ignored.

    return YES;
}

- (void)db_save
{
    if ([pendingLogEntries count] == 0)
    {
        // Nothing to save.
        // The superclass won't likely call us if this is the case, but we're being cautious.
        return;
    }

    BOOL saveOnlyTransaction = ![database isInTransaction];

    if (saveOnlyTransaction)
    {
        [database beginTransaction];
    }

    NSString *cmd = @"INSERT INTO logs (timestamp, level, message) VALUES (?, ?, ?)";
    NSArray *logEntries = nil;

    @synchronized(pendingLogEntries) {
        logEntries = [pendingLogEntries copy];
    }

    for (FMDBLogEntry *logEntry in logEntries)
    {
        [database executeUpdate:cmd, [NSNumber numberWithDouble:floor([logEntry->timestamp timeIntervalSince1970] * 1000)],
                                    [self convertToDbLogLevel:logEntry->level],
                                    logEntry->message];
    }

    @synchronized(pendingLogEntries) {
        [pendingLogEntries removeObjectsInRange:NSMakeRange(0, [logEntries count])];
    }

    if (saveOnlyTransaction)
    {
        [database commit];

        if ([database hadError])
        {
            NSLog(@"%@: Error inserting log entries: code(%d): %@",
                  [self class], [database lastErrorCode], [database lastErrorMessage]);
        }
    }
}

- (void)db_delete
{
    if (_maxAge <= 0.0)
    {
        // Deleting old log entries is disabled.
        // The superclass won't likely call us if this is the case, but we're being cautious.
        return;
    }

    NSDate *maxDate = [NSDate dateWithTimeIntervalSinceNow:(-1.0 * _maxAge)];
    NSNumber *milliseconds = [NSNumber numberWithDouble:floor([maxDate timeIntervalSince1970] * 1000)];
    [self deleteLogs: nil end:milliseconds maxId:nil];
}

- (void)db_saveAndDelete
{
    [database beginTransaction];

    [self db_delete];
    [self db_save];

    [database commit];

    if ([database hadError])
    {
        NSLog(@"%@: Error: code(%d): %@",
              [self class], [database lastErrorCode], [database lastErrorMessage]);
    }
}

- (NSNumber *)convertToDbLogLevel:(NSNumber*)level
{
    LogLevel dbLevel;

    switch ([level intValue]) {
        case DDLogFlagVerbose:
            dbLevel = LOG_LEVEL_VERBOSE;
            break;
        case DDLogFlagDebug:
            dbLevel = LOG_LEVEL_DEBUG;
            break;
        case DDLogFlagInfo:
            dbLevel = LOG_LEVEL_INFO;
            break;
        case DDLogFlagWarning:
            dbLevel = LOG_LEVEL_WARNING;
            break;
        case DDLogFlagError:
            dbLevel = LOG_LEVEL_ERROR;
            break;
        default:
            dbLevel = LOG_LEVEL_UNKNOWN;
            break;
    }

    return [NSNumber numberWithInt:dbLevel];
}

- (BOOL)deleteLogs:(NSNumber*)start end:(NSNumber*)end maxId:(NSNumber*)maxId;
{

    NSMutableArray *whereArray = [[NSMutableArray alloc] init];
    NSMutableArray *args = [[NSMutableArray alloc] init];

    if (start) {
        [whereArray addObject:@"timestamp >= ?"];
        [args addObject:start];
    }

    if (end) {
        [whereArray addObject:@"timestamp <= ?"];
        [args addObject:end];
    }

    if (maxId) {
        [whereArray addObject:@"log_id <= ?"];
        [args addObject:maxId];
    }

    NSString *sql;

    if ([whereArray count] == 0) {
        sql = @"DELETE FROM logs";
    } else {
        sql = [NSString stringWithFormat:@"DELETE FROM logs WHERE %@", [whereArray componentsJoinedByString:@" AND "]];
    }

    BOOL deleteOnlyTransaction = ![database isInTransaction];

    if (deleteOnlyTransaction)
    {
        [database beginTransaction];
    }

    [self db_save];
    BOOL success = [database executeUpdate:sql withArgumentsInArray:args];

    if (deleteOnlyTransaction)
    {

        [database commit];

        if ([database hadError])
        {
            NSLog(@"%@: Error deleting log entries: code(%d): %@",
                  [self class], [database lastErrorCode], [database lastErrorMessage]);
            return FALSE;
        }
    }

    return success;
}

- (NSArray*)getLogs:(NSNumber*)start end:(NSNumber*)end level:(NSNumber*)level limit:(NSNumber*)limit order:(NSString*)order;
{

    [self db_save];

    NSMutableArray *whereArray = [[NSMutableArray alloc] init];
    NSMutableArray *args = [[NSMutableArray alloc] init];

    if (start) {
        [whereArray addObject:@"timestamp >= ?"];
        [args addObject:start];
    }

    if (end) {
        [whereArray addObject:@"timestamp <= ?"];
        [args addObject:end];
    }

    if (level) {
        [whereArray addObject:@"level = ?"];
        [args addObject:level];
    }

    NSString *whereClause = [whereArray count] == 0 ? @"" : [NSString stringWithFormat:@" WHERE %@", [whereArray componentsJoinedByString:@" AND "]];
    NSString *orderClause = order && [order compare:@"desc" options: NSCaseInsensitiveSearch] == NSOrderedSame ? @" ORDER BY timestamp DESC" : @" ORDER BY timestamp ASC";
    NSString *limitClause = limit ? [NSString stringWithFormat:@" LIMIT %@", limit] : @"";
    NSString *query = [NSString stringWithFormat:@"SELECT * FROM logs%@%@%@", whereClause, orderClause, limitClause];

    NSMutableArray *resultList = [[NSMutableArray alloc] init];

    FMResultSet *resultSet = [database executeQuery:query withArgumentsInArray:args];

    while ([resultSet next]) {

        NSNumber *logId = [resultSet objectForColumn:@"log_id"];
        NSNumber *timestamp = [resultSet objectForColumn:@"timestamp"];
        NSNumber *level = [resultSet objectForColumn:@"level"];
        NSString *message = [resultSet stringForColumn:@"message"];

        NSDictionary *row = @{
                @"id": logId,
                @"timestamp": timestamp,
                @"level": level,
                @"message": message
        };

        [resultList addObject:row];
    }

    return resultList;
}

@end
