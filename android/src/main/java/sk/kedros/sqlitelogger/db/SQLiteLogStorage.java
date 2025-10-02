package sk.kedros.sqlitelogger.db;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import ch.qos.logback.core.android.AndroidContextUtil;
import sk.kedros.sqlitelogger.common.LogEvent;
import sk.kedros.sqlitelogger.common.LogLevel;
import sk.kedros.sqlitelogger.common.SortOrder;

public class SQLiteLogStorage {

  private static final String TAG = "SQLiteLogStorage";

  private static final int GET_LOGS_ID_INDEX = 0;
  private static final int GET_LOGS_TIMESTAMP_INDEX = 1;
  private static final int GET_LOGS_LEVEL_INDEX = 2;
  private static final int GET_LOGS_MESSAGE_INDEX  = 3;
  private static final int GET_LOGS_TAG_INDEX  = 4;
  private static final int INSERT_LOG_TIMESTAMP_INDEX = 1;
  private static final int INSERT_LOG_LEVEL_INDEX = 2;
  private static final int INSERT_LOG_MESSAGE_INDEX  = 3;
  private static final int INSERT_LOG_TAG_INDEX  = 4;

  private final SQLiteDatabase db;
  private final File dbFile;

  public SQLiteLogStorage(String logFileDir, String logFileName) {

    this.dbFile = getDatabaseFile(logFileDir, logFileName);

    if (dbFile == null) {
      throw new IllegalArgumentException("Cannot determine database filename");
    }

    try {
      dbFile.getParentFile().mkdirs();
      this.db = SQLiteDatabase.openOrCreateDatabase(dbFile.getPath(), null);
    } catch (SQLiteException e) {
      throw new IllegalArgumentException("Cannot open database", e);
    }

    createOrUpgradeSchema();
  }

  private void createOrUpgradeSchema() {
    int currentVersion = this.db.getVersion();
    Log.d(TAG, "createOrUpgradeSchema version:" + currentVersion);
    if (currentVersion == 0) {
      // initial schema create
      try {
        this.db.execSQL(SQLQuery.CREATE_DB_TABLE);
        this.db.execSQL(SQLQuery.CREATE_DB_INDEX);
        this.db.setVersion(1);
        currentVersion = 1;
        Log.d(TAG, "createOrUpgradeSchema created initial schema.");
      } catch (SQLiteException e) {
        throw new IllegalArgumentException("Cannot create database initial tables", e);
      }
    }

    if (currentVersion == 1) {
      try {
        this.db.execSQL("ALTER TABLE logs ADD COLUMN tag TEXT;");
        this.db.execSQL("CREATE INDEX IF NOT EXISTS i_log_tag ON logs (tag);");
        this.db.setVersion(2);
        currentVersion = 2;
        Log.d(TAG, "createOrUpgradeSchema upgraded schema to v2.");
      } catch (SQLiteException e) {
        throw new IllegalArgumentException("Cannot upgrade database to v2", e);
      }
    }
  }

  private File getDatabaseFile(String logFileDir, String logFileName) {

    File dbFile = null;

    if (logFileName == null || logFileName.trim().isEmpty()) {
      logFileName = "log.sqlite";
    }

    if (logFileDir == null || logFileDir.trim().isEmpty()) {
      dbFile = new File(new AndroidContextUtil().getDatabasePath(logFileName));
    } else {
      dbFile = new File(logFileDir, logFileName);
    }

    return dbFile;
  }

  public File getDbFile() {
    return dbFile;
  }

  public void close() {
    if (db != null) {
      db.close();
    }
  }

  public void insertLog(Long timestamp, LogLevel level, String message) {
    this.insertLog(timestamp, level, message, null);
  }

  public void insertLog(Long timestamp, LogLevel level, String message, String tag) {

    if (level == null || level == LogLevel.UNKNOWN) {
      return;
    }

    SQLiteStatement stmt = db.compileStatement(SQLQuery.INSERT_EVENT);
    stmt.bindLong(INSERT_LOG_TIMESTAMP_INDEX, timestamp);
    stmt.bindLong(INSERT_LOG_LEVEL_INDEX, level.getCode());
    stmt.bindString(INSERT_LOG_MESSAGE_INDEX, message);
    stmt.bindString(INSERT_LOG_TAG_INDEX, tag);

    try {
      db.beginTransaction();
      long eventId = stmt.executeInsert();
      if (eventId != -1) {
        db.setTransactionSuccessful();
      }
    } finally {
      if (db.inTransaction()) {
        db.endTransaction();
      }
      stmt.close();
    }
  }

  public List<LogEvent> getLogs(Long start, Long end, Integer limit, Integer level, List<String> tags, String order, Integer explicitLevel) {

    List<LogEvent> resultList = new ArrayList<>();

    Cursor cursor = null;

    try {

      List<String> selection = new ArrayList<>(3);
      List<String> selectionArgs = new ArrayList<>(3);

      if (start != null) {
        selection.add(SQLQuery.SELECTION_TIMESTAMP_GTE);
        selectionArgs.add(String.valueOf(start));
      }

      if (end != null) {
        selection.add(SQLQuery.SELECTION_TIMESTAMP_LTE);
        selectionArgs.add(String.valueOf(end));
      }

      if (level != null) {
        if (explicitLevel == 1) {
            selection.add(SQLQuery.SELECTION_LEVEL_EQ);
        } else {
            selection.add(SQLQuery.SELECTION_LEVEL_GTE);
        }
        selectionArgs.add(String.valueOf(level));
      }

      if (tags != null && tags.size() > 0) {
        selection.add(" tag IN (" + tags.stream().collect(Collectors.joining("\", \"", "\"", "\"")) + ") ");
      }

      String limitParam = limit == null ? null : String.valueOf(limit);
      SortOrder sortOrder = SortOrder.fromString(order);

      cursor = db.query(
        SQLQuery.TABLE_LOGS,
        SQLQuery.QUERY_GET_LOGS_COLUMNS,
        String.join(" AND ", selection),
        selectionArgs.toArray(new String[0]),
        null,
        null,
        sortOrder == SortOrder.DESC ? SQLQuery.COLUMN_TIMESTAMP + " DESC" : SQLQuery.COLUMN_TIMESTAMP,
        limitParam);

      if (cursor == null) {
        return Collections.emptyList();
      }

      while (cursor.moveToNext()) {
        Log.d(TAG, "+ cursor:" + cursor.getString(GET_LOGS_TAG_INDEX));
        resultList.add(new LogEvent(
          cursor.getLong(GET_LOGS_ID_INDEX),
          cursor.getLong(GET_LOGS_TIMESTAMP_INDEX),
          LogLevel.fromCode(cursor.getInt(GET_LOGS_LEVEL_INDEX)),
          cursor.getString(GET_LOGS_MESSAGE_INDEX),
          cursor.getString(GET_LOGS_TAG_INDEX)
        ));
      }

    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return resultList;
  }

  public void deleteLogs(Long start, Long end, Long maxId) {

    List<String> where = new ArrayList<>(2);
    List<String> whereArgs = new ArrayList<>(2);

    if (start != null) {
      where.add(SQLQuery.SELECTION_TIMESTAMP_GTE);
      whereArgs.add(String.valueOf(start));
    }

    if (end != null) {
      where.add(SQLQuery.SELECTION_TIMESTAMP_LTE);
      whereArgs.add(String.valueOf(end));
    }

    if (maxId != null) {
      where.add(SQLQuery.SELECTION_ID_LTE);
      whereArgs.add(String.valueOf(maxId));
    }

    try {
      db.beginTransaction();
      int deletedRows = db.delete(
        SQLQuery.TABLE_LOGS,
        String.join(" AND ", where),
        whereArgs.toArray(new String[0])
      );
      if (deletedRows > 0) {
        db.setTransactionSuccessful();
      }
    } finally {
      if (db.inTransaction()) {
        db.endTransaction();
      }
    }

  }
}
