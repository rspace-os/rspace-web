package com.researchspace.netfiles;

public enum NfsFileTreeOrderType {
  BY_NAME,
  BY_DATE;

  /**
   * method parsing sort order and returning proper NfsFileTreeOrderType object
   *
   * @param orderType
   * @return
   */
  public static NfsFileTreeOrderType parseOrderTypeString(String orderType) {
    if ("bydate".equalsIgnoreCase(orderType)) {
      return BY_DATE;
    } else {
      return BY_NAME;
    }
  }
}
