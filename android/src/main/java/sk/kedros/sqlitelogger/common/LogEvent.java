package sk.kedros.sqlitelogger.common;

import java.io.Serializable;

public class LogEvent implements Serializable {

  private final Long id;
  private final Long timestamp;
  private final LogLevel level;
  private final String message;
  private final String tag;

  public LogEvent(Long id, Long timestamp, LogLevel level, String message) {
    this(id, timestamp, level, message, null);
  }

  public LogEvent(Long id, Long timestamp, LogLevel level, String message, String tag) {
    this.id = id;
    this.timestamp = timestamp;
    this.level = level;
    this.message = message;
    this.tag = tag;
  }

  public Long getId() {
    return id;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public LogLevel getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

  public String getTag() {
    return tag;
  }

}
