package com.researchspace.api.v2.model;

public record ApiV2CurrentUser(
    Long id,
    String username,
    String email,
    String firstName,
    String lastName,
    Long homeFolderId,
    Long workbenchId,
    boolean hasPiRole,
    boolean hasSysAdminRole,
    String profileImageUrl,
    String profileImageApiUrl,
    Orcid orcid,
    Capabilities capabilities,
    Session session) {

  public record Orcid(boolean available, String id) {}

  public record Capabilities(boolean canUseInventory, boolean canPublish, boolean canViewSystem) {}

  public record Session(
      boolean operatedAs,
      String lastSession,
      boolean canUseDevtools,
      boolean canOverrideFeatureFlags,
      boolean canChangeFeatureFlagBaselines) {}
}
