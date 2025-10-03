import { NativeModules, Platform } from 'react-native';
import util from 'util';

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
  tag: string | null;
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

export interface LogOptions {
  tag: string | undefined;
}

function isLogOptions(obj: any): obj is LogOptions {
  return obj && typeof obj === 'object' && 'tag' in obj;
}

class SQLiteLoggerImpl {
  private _logLevel = LogLevel.Debug;
  private _formatter = defaultFormatter;
  private _originalConsole: {
		debug: typeof console.debug;
		log: typeof console.log;
		info: typeof console.info;
		warn: typeof console.warn;
		error: typeof console.error;
	} | null = null;

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
    // Store original console methods
 	this._originalConsole = {
      debug: console.debug,
      log: console.log,
      info: console.info,
      warn: console.warn,
      error: console.error,
    };
 
    // Override console methods
    console.debug = (...args: any[]) => {
      if (isLogOptions(args[0])) {
        this.debug(util.format(...args.slice(1)), args[0].tag);
        this._originalConsole?.debug(...args.slice(1));
      } else {
        this.debug(util.format(...args));
        this._originalConsole?.debug(...args);
      }
    };
 
    console.log = (...args: any[]) => {
      if (isLogOptions(args[0])) {
        this.info(util.format(...args.slice(1)), args[0].tag);
        this._originalConsole?.log(...args.slice(1));
      } else {
        this.info(util.format(...args));
        this._originalConsole?.log(...args);
      }
    };
 
    console.info = (...args: any[]) => {
      if (isLogOptions(args[0])) {
        this.info(util.format(...args.slice(1)), args[0].tag);
        this._originalConsole?.info(...args.slice(1));
      } else {
        this.info(util.format(...args));
        this._originalConsole?.info(...args);
      }
    };
 
    console.warn = (...args: any[]) => {
      if (isLogOptions(args[0])) {
        this.warn(util.format(...args.slice(1)), args[0].tag);
        this._originalConsole?.warn(...args.slice(1));
      } else {
        this.warn(util.format(...args));
        this._originalConsole?.warn(...args);
      }
    };
 
    console.error = (...args: any[]) => {
      if (isLogOptions(args[0])) {
        this.error(util.format(...args.slice(1)), args[0].tag);
        this._originalConsole?.error(...args.slice(1));
      } else {
        this.error(util.format(...args));
        this._originalConsole?.error(...args);
      }
    };
  }

  disableConsoleCapture() {
    if (this._originalConsole) {
      // restore originals
      console.debug = this._originalConsole.debug;
      console.log = this._originalConsole.log;
      console.info = this._originalConsole.info;
      console.warn = this._originalConsole.warn;
      console.error = this._originalConsole.error;
      this._originalConsole = null;
    }
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
    tags?: string[];
    limit?: number;
    order?: 'asc' | 'desc';
    explicitLevel?: boolean;
  }): Promise<LogEvent[]> {
    return RNSqliteLogger.getLogs({
      ...options,
      explicitLevel: options.explicitLevel ? 1 : 0
    });
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

  trace(msg: string, tag?: string) {
    this.write(LogLevel.Trace, msg, tag);
  }

  debug(msg: string, tag?: string) {
    this.write(LogLevel.Debug, msg, tag);
  }

  info(msg: string, tag?: string) {
    this.write(LogLevel.Info, msg, tag);
  }

  warn(msg: string, tag?: string) {
    this.write(LogLevel.Warning, msg, tag);
  }

  error(msg: string, tag?: string) {
    this.write(LogLevel.Error, msg, tag);
  }

  write(level: LogLevel, msg: string, tag?: string) {
    if (this._logLevel <= level) {
      RNSqliteLogger.write(level, this._formatter(level, msg), tag);
    }
  }

}

// @ts-ignore
const defaultFormatter: LogFormatter = (level, msg) => msg;

export const SQLiteLogger = new SQLiteLoggerImpl();
