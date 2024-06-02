package com.researchspace.service;

import com.researchspace.model.dtos.UserTagData;
import java.util.List;

public interface UserTagManager {

  List<UserTagData> getUserTags(List<Long> userIds);

  void saveUserTags(List<UserTagData> userTags);

  List<String> getAllUserTags(String tagFilter);
}
