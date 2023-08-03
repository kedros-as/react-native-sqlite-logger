package sk.kedros.sqlitelogger.common;

public enum SortOrder {

  NONE,
  ASC,
  DESC;

  public static SortOrder fromString(String order) {

    if (order == null) {
      return NONE;
    }

    switch (order.toLowerCase()) {
      case "asc":
        return ASC;
      case "desc":
        return DESC;
      default:
        return NONE;
    }
  }

}
