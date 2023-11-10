package sk.kedros.sqlitelogger.db;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.database.Cursor;
import io.requery.android.database.sqlite.SQLiteCustomExtension;
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration;
import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteStatement;
import ch.qos.logback.core.android.AndroidContextUtil;
import sk.kedros.sqlitelogger.common.LogEvent;
import sk.kedros.sqlitelogger.common.LogLevel;
import sk.kedros.sqlitelogger.common.SortOrder;
import android.util.Log;

public class SQLiteLogStorage {

  private static final String TAG = "SQLiteLog";

  private static final int GET_LOGS_ID_INDEX = 0;
  private static final int GET_LOGS_TIMESTAMP_INDEX = 1;
  private static final int GET_LOGS_LEVEL_INDEX = 2;
  private static final int GET_LOGS_MESSAGE_INDEX  = 3;
  private static final int INSERT_LOG_TIMESTAMP_INDEX = 1;
  private static final int INSERT_LOG_LEVEL_INDEX = 2;
  private static final int INSERT_LOG_MESSAGE_INDEX  = 3;

  private final SQLiteDatabase db;
  private final File dbFile;
  private final boolean useCompression;

  public SQLiteLogStorage(String logFileDir, String logFileName, boolean useCompression) {

    this.dbFile = getDatabaseFile(logFileDir, logFileName);
    this.useCompression = useCompression;

    if (dbFile == null) {
      throw new IllegalArgumentException("Cannot determine database filename");
    }

    try {
      dbFile.getParentFile().mkdirs();
      this.db = openDatabase();
    } catch (Exception e) {
      close();
      throw new RuntimeException("Cannot open database", e);
    }

    try {
      setUpDatabase();
    } catch (Exception e) {
      close();
      throw new RuntimeException("Cannot setUp database tables", e);
    }
  }

  private SQLiteDatabase openDatabase() {

    if (!useCompression) {
      return SQLiteDatabase.openOrCreateDatabase(dbFile.getPath(), null);
    }

    SQLiteCustomExtension ext = new SQLiteCustomExtension("libsqlite_zstd.so", "sqlite3_sqlitezstd_init");

    SQLiteDatabaseConfiguration conf = new SQLiteDatabaseConfiguration(
      dbFile.getPath(),
      SQLiteDatabase.CREATE_IF_NECESSARY,
      Collections.emptyList(),
      Collections.emptyList(),
      Arrays.asList(ext)
    );

    return SQLiteDatabase.openDatabase(conf, null, null);
  }

  private void setUpCompression() {
    try {
      String query="SELECT zstd_enable_transparent('{\"table\": \"logs\", \"column\": \"message\", \"compression_level\": 19, \"dict_chooser\": \"''a''\"}')";
      final Cursor result = db.rawQuery(query, null);
      if (result == null || !result.moveToFirst()) {
        throw new RuntimeException("SQLite compression: enable_transparent failed!");
      }
      result.close();
    } catch (Exception e) {
      Log.e(TAG, "Error compression setup", e);
    }
  }

  private void setUpDatabase() {
    this.db.execSQL(SQLQuery.CREATE_DB_TABLE);

    if (useCompression) {
      setUpCompression();
    } else {
      this.db.execSQL(SQLQuery.CREATE_DB_INDEX);
    }
  }

  private void compress() {
    if (!useCompression) {
      return;
    }

    try {
      final Cursor result = db.rawQuery("select zstd_incremental_maintenance(null, 1)", null);
      if (result == null || !result.moveToFirst()) {
        throw new RuntimeException("SQLite compression: incremental_maintenance failed!");
      }
      result.close();
    } catch (Exception e) {
      throw new RuntimeException("SQLite compression: incremental_maintenance exception!", e);
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

  public void cleanUp(boolean compress, boolean vacuum) {
    if (compress && useCompression) {
      compress();
    }

    if (vacuum) {
      this.db.execSQL("VACUUM");
    }
  }

  public void close() {
    if (db != null) {
      db.close();
    }
  }
  public void insertLog(Long timestamp, LogLevel level, String message) {

    if (level == null || level == LogLevel.UNKNOWN) {
      return;
    }

    SQLiteStatement stmt = db.compileStatement(useCompression ? SQLQuery.INSERT_EVENT_WITH_COMPRESSION : SQLQuery.INSERT_EVENT);
    stmt.bindLong(INSERT_LOG_TIMESTAMP_INDEX, timestamp);
    stmt.bindLong(INSERT_LOG_LEVEL_INDEX, level.getCode());
    stmt.bindString(INSERT_LOG_MESSAGE_INDEX, message);

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
  public List<LogEvent> getLogs(Long start, Long end, Integer limit, Integer level, String order) {

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
        selection.add(SQLQuery.SELECTION_LEVEL_EQ);
        selectionArgs.add(String.valueOf(level));
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
        resultList.add(new LogEvent(
          cursor.getLong(GET_LOGS_ID_INDEX),
          cursor.getLong(GET_LOGS_TIMESTAMP_INDEX),
          LogLevel.fromCode(cursor.getInt(GET_LOGS_LEVEL_INDEX)),
          cursor.getString(GET_LOGS_MESSAGE_INDEX)
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
        useCompression ? SQLQuery.TABLE_LOGS_WITH_COMPRESSION : SQLQuery.TABLE_LOGS,
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
