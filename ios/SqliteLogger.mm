#import "SqliteLogger.h"

#define LOG_LEVEL_DEF ddLogLevel
#import <CocoaLumberjack/CocoaLumberjack.h>
#import "FMDBLogger.h"

static const DDLogLevel ddLogLevel = DDLogLevelDebug;

@interface SqliteLogger ()
@property (nonatomic, strong) FMDBLogger* sqliteLogger;
@end

@implementation SqliteLogger
RCT_EXPORT_MODULE()

- (NSString *)getSQLiteDirectoryPath
{
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
    NSString *basePath = ([paths count] > 0) ? [paths firstObject] : NSTemporaryDirectory();
    return basePath;
}

RCT_EXPORT_METHOD(configure:(NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {

    NSNumber *deleteInterval = options[@"deleteInterval"] ? : @(60 * 5); // default 5 minutes
    NSString *logFileDir = options[@"logFileDir"] ? : [self getSQLiteDirectoryPath];
    NSString *logFileName = options[@"logFileName"] ? : @"log.sqlite";
    NSNumber *maxAge = options[@"maxAge"] ? : @(60 * 60 * 24 * 5); // default 5 days
    NSNumber *queueSize = options[@"queueSize"] ? : @(500); // default 500 items
    NSNumber *saveInterval = options[@"saveInterval"] ? : @(10); // default 10 seconds
    FMDBLogger *sqliteLogger = [[FMDBLogger alloc] initWithLogDirectory:logFileDir logFileName:logFileName];

    sqliteLogger.saveThreshold     = [queueSize doubleValue];
    sqliteLogger.saveInterval      = [saveInterval doubleValue];
    sqliteLogger.maxAge            = [maxAge doubleValue];
    sqliteLogger.deleteInterval    = [deleteInterval doubleValue];
    sqliteLogger.deleteOnEverySave = NO;

    [DDLog removeAllLoggers];
    self.sqliteLogger = sqliteLogger;
    [DDLog addLogger:sqliteLogger];

    resolve(nil);
}

RCT_EXPORT_METHOD(write:(NSNumber* _Nonnull)level str:(NSString*)str tag:(NSString*)tag) {
    switch (level.integerValue) {
        case LOG_LEVEL_VERBOSE:
            //DDLogVerbose(@"%@", str);
            LOG_MAYBE(LOG_ASYNC_ENABLED, LOG_LEVEL_DEF, DDLogFlagVerbose, 0, tag, __PRETTY_FUNCTION__, @"%@", str);
            break;
        case LOG_LEVEL_DEBUG:
            //DDLogDebug(@"%@", str);
            LOG_MAYBE(LOG_ASYNC_ENABLED, LOG_LEVEL_DEF, DDLogFlagDebug,   0, tag, __PRETTY_FUNCTION__, @"%@", str);
            break;
        case LOG_LEVEL_INFO:
            //DDLogInfo(@"%@", str);
            LOG_MAYBE(LOG_ASYNC_ENABLED, LOG_LEVEL_DEF, DDLogFlagInfo,    0, tag, __PRETTY_FUNCTION__, @"%@", str);
            break;
        case LOG_LEVEL_WARNING:
            //DDLogWarn(@"%@", str);
            LOG_MAYBE(LOG_ASYNC_ENABLED, LOG_LEVEL_DEF, DDLogFlagWarning, 0, tag, __PRETTY_FUNCTION__, @"%@", str);
            break;
        case LOG_LEVEL_ERROR:
            //DDLogError(@"%@", str);
            LOG_MAYBE(NO, LOG_LEVEL_DEF, DDLogFlagError,   0, tag, __PRETTY_FUNCTION__, @"%@", str);
            break;
    }
}

RCT_EXPORT_METHOD(getLogs:(NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSNumber* start = options[@"start"];
    NSNumber* end = options[@"end"];
    NSNumber* level = options[@"level"];
    NSNumber* limit = options[@"limit"];
    NSString* order = options[@"order"];
    NSArray* tags = options[@"tags"];
    NSNumber* explicitLevel = options[@"explicitLevel"];

    NSArray* result = [self.sqliteLogger getLogs:start end:end level:level tags:tags limit:limit order:order explicitLevel:explicitLevel];
    resolve(result);
}

RCT_EXPORT_METHOD(deleteLogs:(NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSNumber* start = options[@"start"];
    NSNumber* end = options[@"end"];
    NSNumber* maxId = options[@"maxId"];

    [self.sqliteLogger deleteLogs:start end:end maxId:maxId];
    resolve(nil);
}

RCT_EXPORT_METHOD(getDbFilePath:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    NSString *path = [self.sqliteLogger getDbFilePath];
    resolve(path);
}

@end
