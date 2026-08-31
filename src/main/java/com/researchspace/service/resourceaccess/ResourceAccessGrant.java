package com.researchspace.service.resourceaccess;

/** Writable resource-role assignment fields accepted from a complete replacement command. */
public record ResourceAccessGrant(String granteeKey, String role) {

  public ResourceAccessGrant {
    if (granteeKey == null || granteeKey.isBlank() || role == null || role.isBlank()) {
      throw new IllegalArgumentException("Resource access grantee key and role must not be blank");
    }
  }
}
