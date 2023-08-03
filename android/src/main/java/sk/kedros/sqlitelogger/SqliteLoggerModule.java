package sk.kedros.sqlitelogger;

import androidx.annotation.NonNull;

import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import sk.kedros.sqlitelogger.common.LogEvent;
import sk.kedros.sqlitelogger.common.LogLevel;
import sk.kedros.sqlitelogger.db.SQLiteAppender;

@ReactModule(name = SqliteLoggerModule.NAME)
public class SqliteLoggerModule extends ReactContextBaseJavaModule {

  private static final Logger logger = LoggerFactory.getLogger(SqliteLoggerModule.class);
  public static final String NAME = "SqliteLogger";

  private String logsDirectory;
  private ReadableMap configureOptions;

  public SqliteLoggerModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  private SQLiteAppender sqLiteAppender;

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void configure(ReadableMap options, Promise promise) {

    try {
      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

      if (sqLiteAppender != null) {
        sqLiteAppender.stop();
      }

      String logFileDir = options.hasKey("logFileDir") ? options.getString("logFileDir") : null;
      String logFileName = options.hasKey("logFileName") ? options.getString("logFileName") : null;
      Long maxAge = options.hasKey("maxAge") ? (long) options.getDouble("maxAge") : null;
      Long deleteInterval = options.hasKey("deleteInterval") ? (long) options.getDouble("deleteInterval") : null;

      sqLiteAppender = new SQLiteAppender();
      sqLiteAppender.setContext(loggerContext);
      sqLiteAppender.setLogFileDir(logFileDir);
      sqLiteAppender.setLogFileName(logFileName);
      sqLiteAppender.setMaxAge(maxAge);
      sqLiteAppender.setDeleteInterval(deleteInterval);
      sqLiteAppender.start();

      ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      root.setLevel(Level.DEBUG);
      root.detachAndStopAllAppenders();
      root.addAppender(sqLiteAppender);

      promise.resolve(null);
    } catch (Throwable t) {
      try {
        if (sqLiteAppender != null) {
          sqLiteAppender.stop();
        }
      } finally {
        promise.reject(t);
      }
    }
  }

  @ReactMethod
  public void write(double level, String str) {
    switch (LogLevel.fromCode((int) level)) {
      case TRACE:
        logger.trace(str);
        break;
      case DEBUG:
        logger.debug(str);
        break;
      case INFO:
        logger.info(str);
        break;
      case WARN:
        logger.warn(str);
        break;
      case ERROR:
        logger.error(str);
        break;
    }
  }

  private WritableMap toMapObject(LogEvent logEvent) {
    WritableMap result = Arguments.createMap();

    result.putDouble("id", (double) logEvent.getId());
    result.putDouble("timestamp", (double) logEvent.getTimestamp());
    result.putInt("level", logEvent.getLevel().getCode());
    result.putString("message", logEvent.getMessage());

    return result;
  }

  @ReactMethod
  public void getLogs(ReadableMap options, Promise promise) {
    try {
      Long start = options.hasKey("start") ? (long) options.getDouble("start") : null;
      Long end = options.hasKey("end") ? (long) options.getDouble("end") : null;
      Integer limit = options.hasKey("limit") ? options.getInt("limit") : null;
      Integer level = options.hasKey("level") ? options.getInt("level") : null;
      String order = options.hasKey("order") ? options.getString("order") : null;
      List<LogEvent> logs = this.sqLiteAppender.getLogStorage().getLogs(start, end, limit, level, order);

      WritableArray result = Arguments.createArray();
      for (LogEvent log: logs) {
        result.pushMap(toMapObject(log));
      }

      promise.resolve(result);
    } catch (Throwable t) {
      promise.reject(t);
    }
  }

  @ReactMethod
  public void deleteLogs(ReadableMap options, Promise promise) {
    try {
      Long start = options.hasKey("start") ? (long) options.getDouble("start") : null;
      Long end = options.hasKey("end") ? (long) options.getDouble("end") : null;
      Long maxId = options.hasKey("maxId") ? (long) options.getDouble("maxId") : null;
      this.sqLiteAppender.getLogStorage().deleteLogs(start, end, maxId);
      promise.resolve(null);
    } catch (Throwable t) {
      promise.reject(t);
    }
  }

  @ReactMethod
  public void getDbFilePath(Promise promise) {
    try {
      File dbFile = this.sqLiteAppender.getLogStorage().getDbFile();
      promise.resolve(dbFile == null ? null : dbFile.getAbsolutePath());
    } catch (Throwable t) {
      promise.reject(t);
    }
  }
}
