package com.researchspace.service.impl;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserTagData;
import com.researchspace.service.UserTagManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Implementation of UserTagManager interface. */
@Service("userTagManager")
public class UserTagManagerImpl implements UserTagManager {

  @Autowired private UserDao userDao;

  public List<String> allTagsCached;

  @Override
  public List<UserTagData> getUserTags(List<Long> userIds) {
    List<UserTagData> result = new ArrayList<>();
    for (Long userId : userIds) {
      User userById = userDao.get(userId);
      result.add(new UserTagData(userId, userById.getTagsList()));
    }
    return result;
  }

  @Override
  public void saveUserTags(List<UserTagData> newUserTags) {
    for (UserTagData userTag : newUserTags) {
      if (userTag != null && userTag.getUserId() != null) {
        User userToUpdate = userDao.get(userTag.getUserId());
        userToUpdate.setTagsList(userTag.getUserTags());
        userDao.save(userToUpdate);
      }
    }
    synchronized (this) {
      allTagsCached = null;
    }
  }

  @Override
  public List<String> getAllUserTags(String tagFilter) {
    List<String> allTags = allTagsCached;
    if (allTagsCached == null) {
      synchronized (this) {
        if (allTagsCached == null) {
          allTagsCached = userDao.getAllUserTags();
        }
        allTags = allTagsCached;
      }
    }
    if (StringUtils.isEmpty(tagFilter)) {
      return allTags;
    }
    List<String> matchingTags =
        allTags.stream().filter(tag -> tag.contains(tagFilter)).collect(Collectors.toList());
    return matchingTags;
  }
}
