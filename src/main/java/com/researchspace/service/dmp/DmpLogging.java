package com.researchspace.service.dmp;

/** Log-safe forms of values that come from a plan service. */
public final class DmpLogging {

  private DmpLogging() {}

  /**
   * @param planId a plan id as stored from the plan service
   * @return the id with line breaks removed, so it cannot start a new log line
   */
  public static String planIdForLog(String planId) {
    return planId == null ? null : planId.replaceAll("[\r\n]", "");
  }
}
