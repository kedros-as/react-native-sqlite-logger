# React-native-sqlite-logger

_A simple sqlite-logger for React Native based on CocoaLumberjack on iOS and Logback on Android._

## Features

- **💆‍♂️ Easy to setup**: Just call `SQLiteLogger.configure()` and you're done. All your existing `console.log/debug/...` calls are automatically logged into a DB

## How it works

React-native-sqlite-logger uses the [undocumented](https://github.com/facebook/react-native/blob/3c9e5f1470c91ff8a161d8e248cf0a73318b1f40/Libraries/polyfills/console.js#L433) `global.__inspectorLog` from React Native. It allows to intercept any calls to `console` and to retrieve the already-formatted log message. React-native-sqlite-logger uses DB-loggers from [CocoaLumberjack](https://github.com/CocoaLumberjack/CocoaLumberjack) on iOS and [Logback Android](https://github.com/tony19/logback-android) on Android to append messages into DB.

## Installation

```sh
npm i react-native-sqlite-logger
npx pod-install
```

## Getting started

```
import { SQLiteLogger } from "react-native-sqlite-logger";

SQLiteLogger.configure();
```

This is all you need to add sqlite-logging to your app. All your existing `console` calls will be appended into a DB. `SQLiteLogger.configure()` also takes several options to customize logging. If you don't want to use `console` calls for logging, you can also use the [direct access API](#direct-access-api).

## API

#### SQLiteLogger.configure(options?): Promise<void>

Initialize the sqlite-logger with the specified options. As soon as the returned promise is resolved, all `console` calls are inserted into a DB. To ensure that no logs are missing, it is good practice to `await` this call at the launch of your app.

| Option           | Description                                                                                                                                                                           | Default                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| `logLevel`       | Minimum log level for DB output (it won't affect console output)                                                                                                                      | LogLevel.Debug              |
| `formatter`      | A function that takes the log level and message and returns the formatted string to write into a DB.                                                                                  | Default format: `${msg}`    |
| `captureConsole` | If `true`, all `console` calls are automatically captured and written into a DB. It can also be changed by calling the `enableConsoleCapture()` and `disableConsoleCapture()` methods | `true`                      |
| `logFileDir`     | Absolute path of directory where the logs are stored in a DB file. If not defined, appropriate directory is automatically chosen.                                                     | `undefined`                 |
| `logFileName`    | Name of the DB file where the logs are stored.                                                                                                                                        | `logs.sqlite`               |
| `maxAge`         | Maximal age (in seconds) of the log messages. Messages older than `maxAge` could be automatically removed.                                                                            | `60 * 60 * 24 * 5` (5 days) |
| `deleteInterval` | How often (in seconds) to delete old log messages. Value lower or equal to zero means that logs won't be deleted.                                                                     | `60 * 5` (5 minutes)        |

#### SQLiteLogger.deleteLogs(options): Promise<void>

Delete logs according to the filter criteria.

| Option  | Description                                |
|---------|--------------------------------------------|
| `start` | Delete logs where `log.timestamp >= start` |
| `end`   | Delete logs where `log.timestamp <= end`   |
| `maxId` | Delete logs where `log.id <= maxId`        |

#### SQLiteLogger.getDbFilePath(): Promise<string>

Returns the absolute path of a DB log file.

#### SQLiteLogger.getLogs(options): Promise<LogEvent[]>

Returns the list of log messages according to the filter criteria.

| Option  | Description                                                                    |
|---------|--------------------------------------------------------------------------------|
| `start` | Fetch logs where `log.timestamp >= start`                                      |
| `end`   | Fetch logs where `log.timestamp <= end`                                        |
| `level` | Fetch logs where `log.level === level`                                         |
| `limit` | Fetch at most `limit` logs in the result list                                  |
| `order` | Order result list by timestamp. Possible values are `asc` (default) and `desc` |

#### SQLiteLogger.enableConsoleCapture()

Enable appending messages from `console` calls into the DB. It is already enabled by default when calling `SQLiteLogger.configure()`.

#### SQLiteLogger.disableConsoleCapture()

After calling this method, `console` calls will no longer be written into the DB.

#### SQLiteLogger.setLogLevel(logLevel)

Change the minimum log level for DB output. The initial log level can be passed as an option to `SQLiteLogger.configure()`.

#### SQLiteLogger.getLogLevel(): LogLevel

Return the current log level.

## Direct access API

If you don't want to use `console` calls for DB logging, you can directly use the following methods to directly insert messages into the DB. It is encouraged to wrap these calls with your own logger API.

### SQLiteLogger.trace(msg)

Shortcut for `SQLiteLogger.write(LogLevel.Trace, msg)`.

### SQLiteLogger.debug(msg)

Shortcut for `SQLiteLogger.write(LogLevel.Debug, msg)`.

### SQLiteLogger.info(msg)

Shortcut for `SQLiteLogger.write(LogLevel.Info, msg)`.

### SQLiteLogger.warn(msg)

Shortcut for `SQLiteLogger.write(LogLevel.Warning, msg)`.

### SQLiteLogger.error(msg)

Shortcut for `SQLiteLogger.write(LogLevel.Error, msg)`.

### SQLiteLogger.write(level, msg)

Append the given message into the DB with the specified log level. The message will be formatted with the `formatter` function specified during the `SQLiteLogger.configure()` call.

## Troubleshooting

### Release build give empty files

If you are using the `console` logger api, please check that you do NOT strip logging from your release build with custom transformers in your `babel.config.js` like [babel-plugin-transform-remove-console](https://github.com/babel/minify/tree/master/packages/babel-plugin-transform-remove-console)
