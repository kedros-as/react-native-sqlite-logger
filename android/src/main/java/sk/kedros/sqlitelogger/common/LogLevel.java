package sk.kedros.sqlitelogger.common;

import ch.qos.logback.classic.Level;

public enum LogLevel {

  UNKNOWN(-1),
  TRACE(10),
  DEBUG(20),
  INFO(30),
  WARN(40),
  ERROR(50);

  private final int code;

  LogLevel(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static LogLevel fromCode(int code) {
    switch (code) {
      case 10:
        return TRACE;
      case 20:
        return DEBUG;
      case 30:
        return INFO;
      case 40:
        return WARN;
      case 50:
        return ERROR;
      default:
        return UNKNOWN;
    }
  }

  public static LogLevel fromLogbackLevel(Level level) {

    if (level == null) {
      return LogLevel.UNKNOWN;
    }

    switch (level.toInt()) {
      case Level.TRACE_INT:
        return LogLevel.TRACE;
      case Level.DEBUG_INT:
        return LogLevel.DEBUG;
      case Level.INFO_INT:
        return LogLevel.INFO;
      case Level.WARN_INT:
        return LogLevel.WARN;
      case Level.ERROR_INT:
        return LogLevel.ERROR;
      default:
        return LogLevel.UNKNOWN;
    }
  }
}
