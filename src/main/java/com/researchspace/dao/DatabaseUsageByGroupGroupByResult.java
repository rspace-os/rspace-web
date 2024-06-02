package com.researchspace.dao;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Comparator;

/**
 * Simple value object to hold aggregated data from database relating to groups. <br>
 * Equality is based on group id.
 */
public class DatabaseUsageByGroupGroupByResult {

  public DatabaseUsageByGroupGroupByResult(BigInteger groupId, Double fileusage) {
    super();
    this.groupId = groupId;
    this.fileusage = fileusage;
  }

  @Override
  public String toString() {
    return "DatabaseUsageByGroupGroupByResult [groupId=" + groupId + ", usage=" + fileusage + "]";
  }

  /** Comparator for sorting by file size. */
  public static class ByUsageComparatorASC
      implements Comparator<DatabaseUsageByGroupGroupByResult>, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(
        DatabaseUsageByGroupGroupByResult arg0, DatabaseUsageByGroupGroupByResult arg1) {
      return arg0.getFileusage().compareTo(arg1.getFileusage());
    }
  }

  /** For hibernate */
  public DatabaseUsageByGroupGroupByResult() {
    super();
  }

  public BigInteger getGroupId() {
    return groupId;
  }

  void setGroupId(BigInteger groupId) {
    this.groupId = groupId;
  }

  void setFileusage(Double usage) {
    if (usage == null) {
      this.fileusage = 0d;
      return;
    }
    this.fileusage = usage;
  }

  private Double countLong;

  Double getCountLong() {
    return countLong;
  }

  void setCountLong(Double countLong) {
    this.countLong = countLong;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
    result = prime * result + ((fileusage == null) ? 0 : fileusage.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    DatabaseUsageByGroupGroupByResult other = (DatabaseUsageByGroupGroupByResult) obj;
    if (groupId == null) {
      if (other.groupId != null) return false;
    } else if (!groupId.equals(other.groupId)) return false;
    if (fileusage == null) {
      if (other.fileusage != null) return false;
    } else if (!fileusage.equals(other.fileusage)) return false;
    return true;
  }

  private BigInteger groupId;

  private Double fileusage = 0d;

  /**
   * REturn Long for convenience
   *
   * @return a number in bytes of file repository usage
   */
  public Double getFileusage() {
    if (countLong != null) return countLong;
    else return fileusage;
  }

  public DatabaseUsageByGroupGroupByResult(Double usage) {
    super();
    this.fileusage = usage;
  }
}
