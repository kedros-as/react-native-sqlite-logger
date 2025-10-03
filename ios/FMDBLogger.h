//
//  FMDBLogger.h
//  SQLiteLogger
//

#import <Foundation/Foundation.h>
#import <CocoaLumberjack/DDAbstractDatabaseLogger.h>

@class FMDatabase;

typedef enum LogLevel {
    LOG_LEVEL_UNKNOWN = -1,
    LOG_LEVEL_VERBOSE = 10,
    LOG_LEVEL_DEBUG = 20,
    LOG_LEVEL_INFO = 30,
    LOG_LEVEL_WARNING = 40,
    LOG_LEVEL_ERROR = 50
} LogLevel;

@interface FMDBLogger : DDAbstractDatabaseLogger <DDLogger>
{
  @private
    NSString *_logDirectory;
    NSString *_logFileName;
    NSMutableArray *pendingLogEntries;

    FMDatabase *database;
}

/**
 * Initializes an instance set to save it's sqlite file to the given directory.
 * If the directory doesn't already exist, it is automatically created.
**/
- (id)initWithLogDirectory:(NSString *)logFileDir logFileName:(NSString *)logFileName;
- (BOOL)deleteLogs:(NSNumber*)start end:(NSNumber*)end maxId:(NSNumber*)maxId;
- (NSArray*)getLogs:(NSNumber*)start end:(NSNumber*)end level:(NSNumber*)level tags:(NSArray*)tags limit:(NSNumber*)limit order:(NSString*)order explicitLevel:(NSNumber*)explicitLevel;
- (NSString*)getDbFilePath;

//
// This class inherits from DDAbstractDatabaseLogger.
//
// So there are a bunch of options such as:
//
// @property (assign, readwrite) NSUInteger saveThreshold;
// @property (assign, readwrite) NSTimeInterval saveInterval;
//
// @property (assign, readwrite) NSTimeInterval maxAge;
// @property (assign, readwrite) NSTimeInterval deleteInterval;
// @property (assign, readwrite) BOOL deleteOnEverySave;
//
// And methods such as:
//
// - (void)savePendingLogEntries;
// - (void)deleteOldLogEntries;
//
// These options and methods are documented extensively in DDAbstractDatabaseLogger.h
//

@end
