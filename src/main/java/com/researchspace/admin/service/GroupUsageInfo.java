package com.researchspace.admin.service;

import com.researchspace.model.Group;

/** DTO for Group listings in system page */
public class GroupUsageInfo {

  private Group group;

  private Long fileUsage;

  private Long totalUsage = 1L;

  public Group getGroup() {
    return group;
  }

  public Long getFileUsage() {
    return fileUsage;
  }

  public Long getTotalUsage() {
    return totalUsage;
  }

  /**
   * Calculates the %age of total usage,
   *
   * @return the %age, or -1 if <code>totalUsage == 0</code>
   */
  public Double getPercent() {
    if (totalUsage != 0) {
      return ((double) fileUsage / (double) totalUsage) * 100;
    } else {
      return -1d;
    }
  }

  public GroupUsageInfo(Group group, Long fileUsage, Long totalUsage) {
    super();
    this.group = group;
    this.fileUsage = fileUsage;
    this.totalUsage = totalUsage;
  }
}
