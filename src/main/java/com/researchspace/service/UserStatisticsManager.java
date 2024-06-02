package com.researchspace.service;

import com.researchspace.model.views.UserStatistics;

/** Manager for user usage stats. */
public interface UserStatisticsManager {

  /**
   * Gets user usage stats.
   *
   * @param daysToCountAsActive
   * @return
   */
  UserStatistics getUserStats(int daysToCountAsActive);
}
