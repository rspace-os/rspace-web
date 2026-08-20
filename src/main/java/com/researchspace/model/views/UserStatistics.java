package com.researchspace.model.views;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A view of basic user metrics of numbers of people per role, retrieved from the DB.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserStatistics {

  private int totalUsers;

  private int totalEnabledUsers;

  private int totalLockedUsers;

  private int totalActiveUsers;

  private int totalEnabledSysAdmins;

  private int totalEnabledRSpaceAdmins;

  public UserStatistics(int totalUsers, int totalEnabledUsers, int totalLockedUsers,
      int totalActiveUsers) {
    this.totalUsers = totalUsers;
    this.totalEnabledUsers = totalEnabledUsers;
    this.totalLockedUsers = totalLockedUsers;
    this.totalActiveUsers = totalActiveUsers;
  }

  /**
   * Gets the total number if used license seats.
   *
   * @return
   */
  public int getUsedLicenseSeats() {
    return getTotalEnabledUsers() -
        (getTotalEnabledRSpaceAdmins() + getTotalEnabledSysAdmins());
  }

  @Override
  public String toString() {
    return "UserStatistics [totalUsers=" + totalUsers + ", totalEnabledUsers=" + totalEnabledUsers
        + ", totalLockedUsers=" + totalLockedUsers + ", totalActiveUsers=" + totalActiveUsers
        + ", totalEnabledSysAdmins=" + totalEnabledSysAdmins + ", totalEnabledRSpaceAdmins="
        + totalEnabledRSpaceAdmins + "]";
  }

}
