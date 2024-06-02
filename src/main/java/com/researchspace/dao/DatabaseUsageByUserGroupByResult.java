package com.researchspace.dao;

import java.io.Serializable;
import java.util.Comparator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Simple value object to hold aggregated data from database relating to users. <br>
 * Equality is based on username.
 */
@EqualsAndHashCode(of = {"username"})
@NoArgsConstructor
@Setter
@Getter
@ToString
public class DatabaseUsageByUserGroupByResult {

  private String username;
  private String usage;
  private Long countLong;

  public DatabaseUsageByUserGroupByResult(String username, String usage) {
    super();
    this.username = username;
    this.usage = usage;
  }

  /** Comparator for sorting by file size. */
  public static class ByUsageComparatorASC
      implements Comparator<DatabaseUsageByUserGroupByResult>, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public int compare(
        DatabaseUsageByUserGroupByResult arg0, DatabaseUsageByUserGroupByResult arg1) {
      return arg0.getUsage().compareTo(arg1.getUsage());
    }
  }

  /**
   * Return Long for convenience
   *
   * @return a number in bytes of file repository usage
   */
  public Long getUsage() {
    if (countLong != null) return countLong;
    else return Long.parseLong(usage);
  }
}
