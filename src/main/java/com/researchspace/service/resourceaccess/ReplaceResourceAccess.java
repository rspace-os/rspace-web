package com.researchspace.service.resourceaccess;

import java.util.List;

/** Complete optimistic replacement of one protected resource's persisted assignments. */
public record ReplaceResourceAccess<ID>(
    ID resourceId, long expectedVersion, List<ResourceAccessGrant> assignments) {

  public ReplaceResourceAccess {
    if (resourceId == null || expectedVersion < 0) {
      throw new IllegalArgumentException("Resource id and non-negative version are required");
    }
    assignments = List.copyOf(assignments);
  }
}
