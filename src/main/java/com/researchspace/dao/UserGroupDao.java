package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import java.util.List;

public interface UserGroupDao extends GenericDao<UserGroup, Long> {

  List<UserGroup> findByUserId(Long userId);

  /**
   * Whether {@code subject} is a PI or lab admin of a group {@code owner} belongs to, reading both
   * users' memberships under shared locks. A concurrent demotion or removal of those memberships
   * waits until the caller's transaction ends, and transfers do not block one another. Call this
   * after locking the record being transferred.
   */
  boolean hasLockedTransferAuthority(User subject, User owner);
}
