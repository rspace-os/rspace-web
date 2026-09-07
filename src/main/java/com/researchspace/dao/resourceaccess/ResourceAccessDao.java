package com.researchspace.dao.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Persistence boundary for locked authorization facts and server-side principal resolution. */
public interface ResourceAccessDao {

  User lockAuthorizationFacts(ResourceAccess access, User subject);

  ResourceRoleAssignment resolveAvailable(
      String granteeKey, String roleKey, String audienceNameSnapshot);

  default ResourceRoleAssignment resolveAvailable(String granteeKey, String roleKey) {
    return resolveAvailable(granteeKey, roleKey, null);
  }

  /** Loads current memberships between assigned users and assigned groups in one bounded query. */
  default Map<Long, Set<Long>> assignedUserGroupIds(ResourceAccess access) {
    return Map.of();
  }

  /** Initializes assignments for a page of access aggregates in one query. */
  default void loadAssignments(Collection<Long> accessIds) {}

  void flush();
}
