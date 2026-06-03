package com.researchspace.dao;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import java.util.List;

/** DAO for the InventoryLink table backing Inventory Link extra-fields. */
public interface InventoryLinkDao extends GenericDao<InventoryLink, Long> {

  /** Returns all non-deleted links whose target equals the given GlobalID (ignoring version). */
  List<InventoryLink> findByTargetGlobalId(String targetGlobalId);

  /**
   * Returns the ExtraLinkField rows whose link points at the supplied target record, matched by
   * (prefix, database id) so that version-pinned links (e.g. {@code SD123v5}) still surface on the
   * base record ({@code SD123}). Filters out soft-deleted fields and soft-deleted links. Used to
   * drive the "referencing items" back-ref lookup by joining each field back to its parent
   * inventory record.
   */
  List<ExtraLinkField> findReferencingLinkFields(GlobalIdPrefix targetPrefix, Long targetDbId);
}
