
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNSqliteLoggerSpec.h"

@interface SqliteLogger : NSObject <NativeSqliteLoggerSpec>
#else
#import <React/RCTBridgeModule.h>

@interface SqliteLogger : NSObject <RCTBridgeModule>
#endif

@end
