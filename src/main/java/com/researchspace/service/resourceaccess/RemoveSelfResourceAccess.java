package com.researchspace.service.resourceaccess;

/** Explicit leave command for one represented subject and protected resource. */
public record RemoveSelfResourceAccess<ID>(ID resourceId) {

  public RemoveSelfResourceAccess {
    if (resourceId == null) {
      throw new IllegalArgumentException("Resource id is required");
    }
  }
}
