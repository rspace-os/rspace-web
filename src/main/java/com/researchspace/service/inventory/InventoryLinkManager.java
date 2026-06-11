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
   * Resolves the audit-backed summary of a link's target (globalId, name, type, deleted) for
   * display, applying read permission. A "latest" link (no stored revision) resolves to the newest
   * revision; a pinned link resolves to its stored {@code targetRevisionId}. This is the
   * service-layer entry point the read/serialization path uses to populate {@link
   * ApiInventoryLink#getTargetSummary()}.
   */
  ApiInventoryLinkTargetSummary getTargetSummary(InventoryLink link, User actor);
}
