package com.researchspace.dao;

import com.researchspace.model.UserGroup;
import java.util.List;

public interface UserGroupDao extends GenericDao<UserGroup, Long> {

  List<UserGroup> findByUserId(Long userId);
}
