package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryLink;
import java.util.List;

/**
 * Service-layer API for managing Inventory Link payloads (the row backing each {@code
 * ExtraLinkField}) and resolving back-references.
 */
public interface InventoryLinkManager {

  /**
   * Persists a new InventoryLink row from the supplied API payload. Does not attach to an
   * ExtraLinkField; the caller is responsible for wiring the returned entity into the parent.
   * Rejects a pin to a version the target does not have with {@code
   * errors.inventory.field.linkTargetVersionNotFound}, checked only after the target proved
   * readable (RSDEV-1530). A Filestore item target is accepted only through a filestore the actor
   * owns and has not removed, at a path naming an item inside it, with no version pin; refusals
   * read like an unknown Global ID target (ADR 0016).
   */
  InventoryLink createLink(ApiInventoryLink apiLink, User actor);

  /**
   * Whether {@link #createLink} would succeed for this payload and actor: a parseable target of an
   * allowed kind, a valid relation type, and, unless the payload skips the target check as CSV
   * import does, a target that exists and the actor can READ and, when pinned, a version that
   * target has; for a Filestore item target, the filestore rule of {@link #createLink}. The same
   * checks as {@code createLink}, so a caller that must not fail as a whole on one bad link (the
   * PIDINST import, RSDEV-1528) can drop that link beforehand. A link field's allowed-relation
   * whitelist is not among them: the field enforces it on write. False for every failure alike,
   * saying nothing about which.
   */
  boolean canCreateLink(ApiInventoryLink apiLink, User actor);

  /** Updates an existing InventoryLink row in place, preserving its created_at timestamp. */
  InventoryLink updateLink(InventoryLink existing, ApiInventoryLink apiLink, User actor);

  /** Marks the link as soft-deleted. */
  void deleteLink(InventoryLink existing, User actor);

  /**
   * Returns the items that link to the given target GlobalID (ignoring version suffix), filtered to
   * sources the requesting user can read. The target itself must exist and be readable by the
   * actor: a malformed id, a missing record and an unreadable record all produce the same "target
   * not found" error, so the response never confirms the existence of a record the caller may not
   * read.
   */
  List<ApiInventoryReferencingItem> findReferencingItems(String targetGlobalId, User actor);

  /**
   * Resolves the audit-backed summary (globalId, name, type, deleted) of the CURRENT state of a
   * link target, applying read permission: unreadable targets degrade to a globalId-only summary so
   * names are never disclosed. A version suffix on the id does not change the state reported (the
   * "Target deleted" pill reflects the record as it is now), but for a readable target it sets
   * {@code pinnedVersionFound} to whether that version has a recorded revision (RSDEV-1530).
   * Malformed ids and prefixes that are not allowed link target kinds are rejected with the same
   * i18n errors as the write path.
   */
  ApiInventoryLinkTargetSummary getTargetSummary(String targetGlobalId, User actor);

  /**
   * Summary of a link target that is a Filestore item. To the user who owns the filestore: its
   * name, type {@code FILESTORE_ITEM}, and {@code deleted} once the filestore was removed from the
   * Gallery. To anyone else, and for an unknown filestore, the same fully redacted summary (ADR
   * 0002, ADR 0016). The remote store is never contacted, so nothing is said about the file itself.
   * A path naming no item inside the filestore is rejected with the write path's "target not found"
   * error.
   */
  ApiInventoryLinkTargetSummary getFilestoreTargetSummary(
      Long filestoreId, String remotePath, User actor);
}
