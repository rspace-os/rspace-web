package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    requiredProperties = {
      "id",
      "username",
      "email",
      "firstName",
      "lastName",
      "homeFolderId",
      "workbenchId",
      "hasPiRole",
      "hasSysAdminRole",
      "profileImageUrl",
      "profileImageApiUrl",
      "orcid",
      "capabilities",
      "livechat",
      "session"
    })
public record ApiV2CurrentUser(
    @Schema(description = "Stable user identifier.", format = "int64", example = "42") Long id,
    @Schema(description = "RSpace login name.", example = "ada") String username,
    @Schema(description = "Current user's email address.", format = "email") String email,
    @Schema(description = "Current user's given name.") String firstName,
    @Schema(description = "Current user's family name.") String lastName,
    @Schema(description = "Root folder identifier.", format = "int64", nullable = true)
        Long homeFolderId,
    @Schema(description = "Inventory workbench identifier.", format = "int64", nullable = true)
        Long workbenchId,
    @Schema(description = "Whether the user has the principal-investigator role.")
        boolean hasPiRole,
    @Schema(description = "Whether the user has the system-administrator role.")
        boolean hasSysAdminRole,
    @Schema(description = "Legacy profile-image URL.", format = "uri-reference", nullable = true)
        String profileImageUrl,
    @Schema(
            description = "REST API v2 profile-image URL.",
            format = "uri-reference",
            nullable = true)
        String profileImageApiUrl,
    @Schema(description = "ORCID availability and connection state.") Orcid orcid,
    @Schema(description = "Capabilities enabled for the current user.") Capabilities capabilities,
    @Schema(description = "Live-chat configuration for the current user.") LiveChat livechat,
    @Schema(description = "State of the current authenticated session.") Session session) {

  @Schema(requiredProperties = {"available", "id"})
  public record Orcid(
      @Schema(description = "Whether ORCID integration is available.") boolean available,
      @Schema(
              description = "Connected ORCID identifier.",
              pattern = "^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$",
              example = "0000-0002-1825-0097",
              nullable = true)
          String id) {}

  @Schema(requiredProperties = {"canUseInventory", "canPublish", "canViewSystem"})
  public record Capabilities(
      @Schema(description = "Whether inventory is available.") boolean canUseInventory,
      @Schema(description = "Whether public sharing is available.") boolean canPublish,
      @Schema(description = "Whether system-administration views are available.")
          boolean canViewSystem) {}

  @Schema(requiredProperties = {"enabled", "serverKey"})
  public record LiveChat(
      @Schema(description = "Whether live chat is enabled.") boolean enabled,
      @Schema(description = "Browser live-chat application key.", nullable = true)
          String serverKey) {}

  @Schema(
      requiredProperties = {
        "operatedAs",
        "lastSession",
        "canUseDevtools",
        "canOverrideFeatureFlags",
        "canChangeFeatureFlagBaselines"
      })
  public record Session(
      @Schema(description = "Whether a system administrator is operating as another user.")
          boolean operatedAs,
      @Schema(
              description = "Previous successful login instant.",
              format = "date-time",
              nullable = true,
              example = "2026-08-01T20:00:00Z")
          String lastSession,
      @Schema(description = "Whether the feature-flag developer tools are available.")
          boolean canUseDevtools,
      @Schema(description = "Whether the user may override feature flags.")
          boolean canOverrideFeatureFlags,
      @Schema(description = "Whether the user may change feature-flag baselines.")
          boolean canChangeFeatureFlagBaselines) {}
}
