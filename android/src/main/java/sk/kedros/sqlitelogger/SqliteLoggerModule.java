package sk.kedros.sqlitelogger;

import android.os.Build;

import androidx.annotation.NonNull;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import sk.kedros.sqlitelogger.common.LogEvent;
import sk.kedros.sqlitelogger.common.LogLevel;
import sk.kedros.sqlitelogger.db.SQLiteAppender;

import android.util.Log;

@ReactModule(name = SqliteLoggerModule.NAME)
public class SqliteLoggerModule extends ReactContextBaseJavaModule {

  private static final Logger logger = LoggerFactory.getLogger(SqliteLoggerModule.class);
  public static final String NAME = "SqliteLogger";

  private final ExecutorService executor;
  private String logsDirectory;
  private ReadableMap configureOptions;

  public SqliteLoggerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    executor = Executors.newSingleThreadExecutor();
  }

  private SQLiteAppender sqLiteAppender;
  private AsyncAppender asyncAppender;

  private boolean isCompressionExtensionAvailable() {
    for (String abi: Build.SUPPORTED_ABIS) {
      switch (abi) {
        case "arm64-v8a":
        case "x86_64":
        case "x86":
        case "armeabi-v7a":
          return true;
      }
    }
    return false;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void configure(ReadableMap options, Promise promise) {

    try {
      removeAllAppenders();

      LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

      String logFileDir = options.hasKey("logFileDir") ? options.getString("logFileDir") : null;
      String logFileName = options.hasKey("logFileName") ? options.getString("logFileName") : null;
      Long maxAge = options.hasKey("maxAge") ? (long) options.getDouble("maxAge") : null;
      Long deleteInterval = options.hasKey("deleteInterval") ? (long) options.getDouble("deleteInterval") : null;
      Boolean async = options.hasKey("async") ? options.getBoolean("async") : Boolean.TRUE;
      Integer queueSize = options.hasKey("queueSize") ? (int) options.getDouble("queueSize") : null;
      Integer maxFlushTime = options.hasKey("maxFlushTime") ? (int) options.getDouble("maxFlushTime") : null;
      Boolean useCompression = options.hasKey("useCompression") ? options.getBoolean("useCompression") : Boolean.FALSE;

      sqLiteAppender = new SQLiteAppender();
      sqLiteAppender.setContext(loggerContext);
      sqLiteAppender.setLogFileDir(logFileDir);
      sqLiteAppender.setLogFileName(logFileName);
      sqLiteAppender.setMaxAge(maxAge);
      sqLiteAppender.setName("SQLITE");
      sqLiteAppender.setDeleteInterval(deleteInterval);
      sqLiteAppender.setUseCompression(useCompression && isCompressionExtensionAvailable());
      sqLiteAppender.start();

      Appender<ILoggingEvent> appender;
      if (async) {
        asyncAppender = new AsyncAppender();
        if (queueSize != null) {
          asyncAppender.setQueueSize(queueSize);
        }
        if (maxFlushTime != null) {
          asyncAppender.setMaxFlushTime(maxFlushTime);
        } else {
          asyncAppender.setMaxFlushTime(0);
        }
        asyncAppender.setContext(loggerContext);
        asyncAppender.setName("ASYNC");
        asyncAppender.addAppender(sqLiteAppender);
        asyncAppender.setDiscardingThreshold(0);
        asyncAppender.setIncludeCallerData(false);
        asyncAppender.start();
        appender = asyncAppender;
      } else {
        appender = sqLiteAppender;
      }

      ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      root.setLevel(Level.DEBUG);
      root.detachAndStopAllAppenders();
      root.addAppender(appender);

      promise.resolve(null);
    } catch (Throwable t) {
      try {
        try {
          removeAllAppenders();
        } catch (Exception e) {
          // intentionally left blank
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

  private void executeAsyncTask(Promise promise, Runnable runnable) {
    try {
      executor.execute(() -> {
        runnable.run();
      });
    } catch (Throwable t) {
      promise.reject(t);
    }
  }

  private void removeAllAppenders() throws Exception {
    Exception error = null;

    try {
      if (asyncAppender != null) {
        asyncAppender.stop();
      }
    } catch (Exception e) {
      error = e;
    } finally {
      asyncAppender = null;
    }

    try {
      if (sqLiteAppender != null) {
        sqLiteAppender.stop();
      }
    } catch (Exception e) {
      error = e;
    } finally {
      sqLiteAppender = null;
    }

    if (error != null) {
      throw error;
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
    executeAsyncTask(promise, () -> {
      try {
        Long start = options.hasKey("start") ? (long) options.getDouble("start") : null;
        Long end = options.hasKey("end") ? (long) options.getDouble("end") : null;
        Integer limit = options.hasKey("limit") ? options.getInt("limit") : null;
        Integer level = options.hasKey("level") ? options.getInt("level") : null;
        String order = options.hasKey("order") ? options.getString("order") : null;
        List<LogEvent> logs = this.sqLiteAppender.getLogStorage().getLogs(start, end, limit, level, order);

        WritableArray result = Arguments.createArray();
        for (LogEvent log : logs) {
          result.pushMap(toMapObject(log));
        }

        promise.resolve(result);
      } catch (Throwable t) {
        promise.reject(t);
      }
    });
  }

  @ReactMethod
  public void deleteLogs(ReadableMap options, Promise promise) {
    executeAsyncTask(promise, () -> {
      try {
        Long start = options.hasKey("start") ? (long) options.getDouble("start") : null;
        Long end = options.hasKey("end") ? (long) options.getDouble("end") : null;
        Long maxId = options.hasKey("maxId") ? (long) options.getDouble("maxId") : null;
        this.sqLiteAppender.getLogStorage().deleteLogs(start, end, maxId);
        promise.resolve(null);
      } catch (Throwable t) {
        promise.reject(t);
      }
    });
  }

  @ReactMethod
  public void getDbFilePath(Promise promise){
    executeAsyncTask(promise, () -> {
      try {
        File dbFile = this.sqLiteAppender.getLogStorage().getDbFile();
        promise.resolve(dbFile == null ? null : dbFile.getAbsolutePath());
      } catch (Throwable t) {
        promise.reject(t);
      }
    });
  }

  @ReactMethod
  public void cleanUp(ReadableMap options, Promise promise) {

    if (sqLiteAppender == null) {
      promise.resolve(null);
      return;
    }

    Boolean compress = this.sqLiteAppender.isUseCompression() && options.hasKey("compress") ? options.getBoolean("compress") : Boolean.TRUE;
    Boolean vacuum = options.hasKey("vacuum") ? options.getBoolean("vacuum") : Boolean.FALSE;

    if (!compress && !vacuum) {
      promise.resolve(null);
      return;
    }

    executeAsyncTask(promise, () -> {
      try {
        this.sqLiteAppender.getLogStorage().cleanUp(compress, vacuum);
        promise.resolve(null);
      } catch (Throwable t) {
        promise.reject(t);
      }
    });
  }
}
