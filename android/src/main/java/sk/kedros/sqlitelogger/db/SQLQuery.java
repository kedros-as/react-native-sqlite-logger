package sk.kedros.sqlitelogger.db;

abstract class SQLQuery {

  public static final String COLUMN_TIMESTAMP = "timestamp";

  public static final String CREATE_DB_TABLE = new StringBuilder("CREATE TABLE IF NOT EXISTS logs ( ")
    .append("log_id INTEGER PRIMARY KEY AUTOINCREMENT, ")
    .append("timestamp INTEGER, ")
    .append("level TINYINT, ")
    .append("message TEXT ")
    .append(");")
    .toString();

  public static final String CREATE_DB_INDEX = "CREATE INDEX IF NOT EXISTS i_log_timestamp ON logs (timestamp);";

  public static final String INSERT_EVENT = "INSERT INTO logs (timestamp, level, message) VALUES (?, ?, ?)";

  public static final String INSERT_EVENT_WITH_COMPRESSION = "INSERT INTO _logs_zstd (timestamp, level, message, _message_dict) VALUES (?, ?, ?, null)";

  public static final String[] QUERY_GET_LOGS_COLUMNS = new String[] {"log_id", "timestamp", "level", "message"};

  public static final String SELECTION_ID_LTE = "log_id <= ?";
  public static final String SELECTION_LEVEL_EQ = "level = ?";
  public static final String SELECTION_TIMESTAMP_GTE = "timestamp >= ?";
  public static final String SELECTION_TIMESTAMP_LTE = "timestamp <= ?";

  public static final String TABLE_LOGS = "logs";
  public static final String TABLE_LOGS_WITH_COMPRESSION = "_logs_zstd";
}
