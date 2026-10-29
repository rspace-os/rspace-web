package com.researchspace.dao.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Persistence boundary for locked authorization facts and server-side principal resolution. */
public interface ResourceAccessDao {

  AuthorizationFacts lockAuthorizationFacts(ResourceAccess access, User subject);

  /** Current write-time principal facts read under the resource-access transaction locks. */
  record AuthorizationFacts(
      User subject, boolean enabled, Set<Long> currentGroupIds, Set<String> globalRoleNames) {
    public AuthorizationFacts {
      Objects.requireNonNull(subject, "subject");
      currentGroupIds = Set.copyOf(currentGroupIds);
      globalRoleNames = Set.copyOf(globalRoleNames);
    }
  }

  ResourceRoleAssignment resolveAvailable(
      String granteeKey, String roleKey, String audienceNameSnapshot);

  /** Loads current memberships between assigned users and assigned groups in one bounded query. */
  Map<Long, Set<Long>> assignedUserGroupIds(ResourceAccess access);

  /** Initializes assignments for a page of access aggregates in one query. */
  void loadAssignments(Collection<Long> accessIds);

  /** Returns the ids of assigned groups that have at least one enabled member, in one query. */
  Set<Long> availableGroupIds(Collection<Long> groupIds);

  void flush();
}
