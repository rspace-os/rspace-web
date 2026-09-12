package com.researchspace.model.collection;

import java.util.Set;

/** Internal correlated membership rule for a protected resource's access aggregate. */
public record ResourceRoleMembershipConstraint(
    String resourceAccessIdPath,
    long subjectId,
    Set<Long> currentGroupIds,
    Set<String> readableRoleKeys,
    boolean includeAllUsers)
    implements QueryConstraint {

  public ResourceRoleMembershipConstraint {
    if (resourceAccessIdPath == null
        || !resourceAccessIdPath.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")) {
      throw new IllegalArgumentException("Resource access id path is invalid");
    }
    if (subjectId == 0) {
      throw new IllegalArgumentException("Resource access subject id must be non-zero");
    }
    currentGroupIds = Set.copyOf(currentGroupIds);
    readableRoleKeys = Set.copyOf(readableRoleKeys);
    if (readableRoleKeys.isEmpty()
        || readableRoleKeys.stream().anyMatch(role -> role == null || role.isBlank())) {
      throw new IllegalArgumentException("Readable resource roles must not be empty");
    }
  }
}
