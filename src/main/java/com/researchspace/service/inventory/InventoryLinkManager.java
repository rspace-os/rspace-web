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
   */
  InventoryLink createLink(ApiInventoryLink apiLink, User actor);

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
   * names are never disclosed. Any version suffix on the id is ignored, since the summary backs the
   * "Target deleted" pill, which reflects the record as it is now rather than as pinned. Malformed
   * ids and prefixes that are not allowed link target kinds are rejected with the same i18n errors
   * as the write path.
   */
  ApiInventoryLinkTargetSummary getTargetSummary(String targetGlobalId, User actor);
}
