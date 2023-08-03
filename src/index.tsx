import { NativeModules, Platform } from 'react-native';
declare var global: any;

const LINKING_ERROR =
  `The package 'react-native-sqlite-logger' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const RNSqliteLogger = NativeModules.SqliteLogger
  ? NativeModules.SqliteLogger
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export enum LogLevel {
  Trace = 10,
  Debug = 20,
  Info = 30,
  Warning = 40,
  Error = 50,
}

export interface LogEvent {
  id: number;
  timestamp: number;
  level: LogLevel;
  message: string;
}

export type LogFormatter = (level: LogLevel, msg: string) => string;

export interface ConfigureOptions {
  /**
   * Capture `console.[log|trace|debug|info|warning|error]` invocations and log these messages into the DB.
   **/
  captureConsole?: boolean;
  /**
   * Interval between deleting of the log messages that are older than {@link maxAge}.
   **/
  deleteInterval?: number;
  /**
   * Custom log message formatter.
   **/
  formatter?: LogFormatter;
  /**
   * Directory of the DB file.
   **/
  logFileDir?: string;
  /**
   * Name of the DB file.
   **/
  logFileName?: string;
  /**
   * Minimum logging level.
   **/
  logLevel?: LogLevel;
  /**
   * Maximal age of the logs to preserve in seconds.
   **/
  maxAge?: number;
}

class SQLiteLoggerImpl {
  private _logLevel = LogLevel.Debug;
  private _formatter = defaultFormatter;

  async configure(options: ConfigureOptions = {}): Promise<void> {
    const {
      captureConsole = true,
      formatter = defaultFormatter,
      logFileDir,
      logFileName,
      logLevel = LogLevel.Debug,
      maxAge,
      deleteInterval,
    } = options;

    await RNSqliteLogger.configure({
      deleteInterval,
      logFileDir,
      logFileName,
      maxAge,
    });

    this._logLevel = logLevel;
    this._formatter = formatter;

    if (captureConsole) {
      this.enableConsoleCapture();
    } else {
      this.disableConsoleCapture();
    }
  }

  enableConsoleCapture() {
    // __inspectorLog is an undocumented feature of React Native
    // that allows to intercept calls to console.debug/log/warn/error
    global.__inspectorLog = this._handleLog;
  }

  disableConsoleCapture() {
    global.__inspectorLog = undefined;
  }

  setLogLevel(logLevel: LogLevel) {
    this._logLevel = logLevel;
  }

  getLogLevel(): LogLevel {
    return this._logLevel;
  }

  getLogs(options: {
    start?: number;
    end?: number;
    level?: LogLevel;
    limit?: number;
    order?: 'asc' | 'desc';
  }): Promise<LogEvent[]> {
    return RNSqliteLogger.getLogs(options);
  }

  deleteLogs(options: {
    start?: number;
    end?: number;
    maxId?: number;
  }): Promise<void> {
    return RNSqliteLogger.deleteLogs(options);
  }

  getDbFilePath(): Promise<string> {
    return RNSqliteLogger.getDbFilePath();
  }

  trace(msg: string) {
    this.write(LogLevel.Trace, msg);
  }

  debug(msg: string) {
    this.write(LogLevel.Debug, msg);
  }

  info(msg: string) {
    this.write(LogLevel.Info, msg);
  }

  warn(msg: string) {
    this.write(LogLevel.Warning, msg);
  }

  error(msg: string) {
    this.write(LogLevel.Error, msg);
  }

  write(level: LogLevel, msg: string) {
    if (this._logLevel <= level) {
      RNSqliteLogger.write(level, this._formatter(level, msg));
    }
  }

  private _handleLog = (level: string, msg: string) => {
    switch (level) {
      case 'trace':
        this.trace(msg);
        break;
      case 'debug':
        this.debug(msg);
        break;
      case 'log':
        this.info(msg);
        break;
      case 'warning':
        this.warn(msg);
        break;
      case 'error':
        this.error(msg);
        break;
    }
  };
}

// @ts-ignore
const defaultFormatter: LogFormatter = (level, msg) => msg;

export const SQLiteLogger = new SQLiteLoggerImpl();
