package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

/**
 * Structure search over Inventory: finds the Inventory records that a Gallery chemistry file
 * matching a chemical structure is attached to (structure hits, see CONTEXT.md).
 */
public interface InventoryStructureSearchManager {

  /**
   * Whether structure search can run for this user: the deployment uses the Indigo chemistry
   * service, the system administrator allows the Chemistry app, and the user has enabled it. These
   * are the same conditions the Workspace uses to offer its chemical search.
   *
   * @param user the searching user
   * @return true if {@link #searchByStructure} can be used
   */
  boolean isStructureSearchAvailable(User user);

  /**
   * Returns the Inventory records the user can read that match a structure search the caller has
   * already run through the chemistry service. The chemistry service is called outside this
   * transactional manager, as the Workspace search does, so a slow service holds no database
   * transaction.
   *
   * <p>Each record that carries a matching Gallery chemistry file, as a record attachment or in an
   * attachment field, is returned once. Matches are not rolled up to a parent or down to children.
   * With a text query, the structure hits further limit a normal Inventory text search, which only
   * considers Lucene's top hits.
   *
   * <p>At most 1000 hits that pass every filter are loaded, then sorted and paged. When more match,
   * the result's {@code structureSearchLimitReached} is set.
   *
   * @param chemIds chem element ids reported by the chemistry service, in its order
   * @param textQuery (optional) text query to combine with the structure
   * @param resultType (optional) keep only records of this type; {@code null} means all
   * @param ownedBy (optional) keep only records owned by this username
   * @param deletedOption (optional) deleted-items option; {@code null} means exclude deleted
   * @param bookable (optional) keep only bookable ({@code true}) or non-bookable ({@code false})
   *     records
   * @param pgCrit paging and sort order
   * @param user the searching user
   * @return a page of matching records
   */
  ApiInventorySearchResult searchByStructure(
      List<Long> chemIds,
      String textQuery,
      InventorySearchType resultType,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      Boolean bookable,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);

  /**
   * The structure hits for chem element ids the chemistry service has already returned, for listing
   * Inventory items in the Workspace chemical search. Applies the same rules as {@link
   * #searchByStructure} with no optional filters: records the user can read, of every type, not
   * deleted. Stops silently at the same 1000-hit limit.
   *
   * @param chemIds chem element ids reported by the chemistry service, in its order
   * @param user the searching user
   * @return the hits, each record once, ordered by the first chem element id that matched it
   */
  List<InventoryStructureHit> findReadableStructureHits(List<Long> chemIds, User user);
}
