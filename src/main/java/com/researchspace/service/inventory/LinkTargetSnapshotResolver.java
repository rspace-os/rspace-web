package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;

/**
 * Resolves an Inventory link's target through the Envers audit ({@code _AUD}) tables rather than
 * the live record. A link is either "latest" (resolves dynamically to the newest non-delete
 * revision) or "pinned" (resolves to one specific revision captured at pin time).
 */
public interface LinkTargetSnapshotResolver {

  /**
   * Resolves the Envers revision (REV) that a user-facing target version maps to. Used at pin time
   * to capture the exact audit row alongside the display version. Returns {@code null} when the
   * version is null or the target family has no user-facing versioning (e.g. notebooks).
   *
   * @param prefix the target's GlobalID prefix (SD, NB, GL, SA, SS, IC, IN)
   * @param dbId the target's database id
   * @param version the user-facing version being pinned, or null for "latest"
   */
  Long resolveRevisionForVersion(GlobalIdPrefix prefix, Long dbId, Long version);

  /**
   * Resolves a link target to a summary built from its audit snapshot. When {@code
   * targetRevisionId} is set the exact revision is loaded; otherwise the newest non-delete revision
   * is used ("latest").
   *
   * <p>Authorization: the summary's name and type are populated only when the actor may READ the
   * target (live read permission, including soft-deleted, or owner of the snapshot when only audit
   * data remains after a hard delete). Otherwise a minimal summary carrying only the globalId is
   * returned, so an inaccessible target is never disclosed by name.
   *
   * @param prefix the target's GlobalID prefix
   * @param dbId the target's database id
   * @param versionPin the user-facing version for display, or null for "latest"
   * @param targetRevisionId the exact Envers revision to load, or null for "latest"
   * @param user the actor whose read permission gates disclosure
   */
  ApiInventoryLinkTargetSummary resolveSummary(
      GlobalIdPrefix prefix, Long dbId, Long versionPin, Long targetRevisionId, User user);
}
