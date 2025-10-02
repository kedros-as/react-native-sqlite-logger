package sk.kedros.sqlitelogger.db;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.android.AndroidContextUtil;
import ch.qos.logback.core.util.Duration;
import sk.kedros.sqlitelogger.common.LogLevel;

public class SQLiteAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static final long DEFAULT_DELETE_INTERVAL = 5 * 60; // 5 minutes
  private static final long DEFAULT_MAX_AGE = 5 * 24 * 60 * 60; // cca 5 days (in seconds)

  private SQLiteLogStorage logStorage;
  private String logFileDir;
  private String logFileName;
  private long maxAge;
  private long lastCleanupTime = 0;
  private long deleteInterval;

  public String getLogFileDir() {
    return logFileDir;
  }

  public void setLogFileDir(String logFileDir) {
    this.logFileDir = logFileDir;
  }

  public String getLogFileName() {
    return logFileName;
  }

  public void setLogFileName(String logFileName) {
    this.logFileName = logFileName;
  }

  public long getMaxAge() {
    return maxAge;
  }

  public void setMaxAge(Long maxAge) {
    this.maxAge = (maxAge == null ? DEFAULT_MAX_AGE : maxAge) * 1000;
  }

  public long getDeleteInterval() {
    return deleteInterval;
  }

  public void setDeleteInterval(Long deleteInterval) {
    this.deleteInterval = (deleteInterval == null ? DEFAULT_DELETE_INTERVAL : deleteInterval) * 1000;
  }

  /*
   * (non-Javadoc)
   * @see ch.qos.logback.core.UnsynchronizedAppenderBase#start()
   */
  @Override
  public void start() {
    this.started = false;
    try {
      if (logStorage != null) {
        logStorage.close();
      }
      logStorage = new SQLiteLogStorage(this.logFileDir, this.logFileName);
      clearExpiredLogs();
      super.start();
    } catch (Exception e) {
      addError(e.getMessage(), e);
    }
  }

  /**
   * Removes expired logs from the database
   * @param db
   */
  private void clearExpiredLogs() {

    if (this.deleteInterval <= 0 || this.maxAge <= 0) {
      return;
    }

    final long now = System.currentTimeMillis();

    if (lastCleanupTime <= 0 || now - lastCleanupTime >= deleteInterval) {
        lastCleanupTime = now;
        this.logStorage.deleteLogs(null, now - maxAge, null);
    }

  }

  /*
   * (non-Javadoc)
   * @see java.lang.Object#finalize()
   */
  @Override
  protected void finalize() throws Throwable {
    if (this.logStorage != null) {
      this.logStorage.close();
    }
  }

  /*
   * (non-Javadoc)
   * @see ch.qos.logback.core.UnsynchronizedAppenderBase#stop()
   */
  @Override
  public void stop() {
    if (this.logStorage != null) {
      try {
        this.logStorage.close();
      } finally {
        this.logStorage = null;
      }
    }
    this.lastCleanupTime = 0;
  }

  /*
   * (non-Javadoc)
   * @see ch.qos.logback.core.UnsynchronizedAppenderBase#append(java.lang.Object)
   */
  @Override
  public void append(ILoggingEvent event) {

    if (!isStarted()) {
      return;
    }

    try {
      clearExpiredLogs();
      logStorage.insertLog(
        event.getTimeStamp(),
        LogLevel.fromLogbackLevel(event.getLevel()),
        event.getFormattedMessage(),
        event.getLoggerName()
      );
    } catch (Throwable e) {
      addError("Cannot append event", e);
    }
  }

  public SQLiteLogStorage getLogStorage() {
    return logStorage;
  }
}
