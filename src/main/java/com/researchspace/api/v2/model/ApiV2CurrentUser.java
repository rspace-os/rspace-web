package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiV2CurrentUser(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stable user identifier.",
            format = "int64",
            example = "42")
        Long id,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "RSpace login name.",
            example = "ada")
        String username,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Current user's email address.",
            format = "email")
        String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Current user's given name.")
        String firstName,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Current user's family name.")
        String lastName,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Root folder identifier.",
            format = "int64",
            nullable = true)
        Long homeFolderId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Inventory workbench identifier.",
            format = "int64",
            nullable = true)
        Long workbenchId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Whether the user has the principal-investigator role.")
        boolean hasPiRole,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Whether the user has the system-administrator role.")
        boolean hasSysAdminRole,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Legacy profile-image URL.",
            format = "uri-reference",
            nullable = true)
        String profileImageUrl,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "REST API v2 profile-image URL.",
            format = "uri-reference",
            nullable = true)
        String profileImageApiUrl,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "ORCID availability and connection state.")
        Orcid orcid,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Capabilities enabled for the current user.")
        Capabilities capabilities,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Live-chat configuration for the current user.")
        LiveChat livechat,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "State of the current authenticated session.")
        Session session) {

  public record Orcid(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether ORCID integration is available.")
          boolean available,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Connected ORCID identifier.",
              pattern = "^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$",
              example = "0000-0002-1825-0097",
              nullable = true)
          String id) {}

  public record Capabilities(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether inventory is available.")
          boolean canUseInventory,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether public sharing is available.")
          boolean canPublish,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether system-administration views are available.")
          boolean canViewSystem) {}

  public record LiveChat(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether live chat is enabled.")
          boolean enabled,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Browser live-chat application key.",
              nullable = true)
          String serverKey) {}

  @Schema(
      requiredProperties = {
        "canUseDevtools",
        "canOverrideFeatureFlags",
        "canChangeFeatureFlagBaselines"
      })
  public record Session(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Whether a system administrator is operating as another user.")
          boolean operatedAs,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Previous successful login instant.",
              format = "date-time",
              nullable = true,
              example = "2026-08-01T20:00:00Z")
          String lastSession,
      @Schema(
              description = "Whether the feature-flag developer tools are available.")
          boolean canUseDevtools,
      @Schema(
              description = "Whether the user may override feature flags.")
          boolean canOverrideFeatureFlags,
      @Schema(
              description = "Whether the user may change feature-flag baselines.")
          boolean canChangeFeatureFlagBaselines) {}
}
