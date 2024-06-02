package com.researchspace.service.impl;

import com.researchspace.dao.UserDao;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.UserStatisticsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Implementation of UserStatisticsManager interface. */
@Service("userStatisticsManager")
public class UserStatisticsManagerImpl implements UserStatisticsManager {

  @Autowired private UserDao userDao;

  @Override
  public UserStatistics getUserStats(int daysToCountAsActive) {
    return userDao.getUserStats(daysToCountAsActive);
  }
}
