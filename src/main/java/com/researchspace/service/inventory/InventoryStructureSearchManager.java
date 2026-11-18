package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;

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
   * Runs a structure search through the chemistry service, the same search the Workspace uses, and
   * returns the matching Inventory records the user can read.
   *
   * <p>Each record that carries a matching Gallery chemistry file, as a record attachment or in an
   * attachment field, is returned once. Matches are not rolled up to a parent or down to children.
   * With a text query, the structure hits further limit a normal Inventory text search, which only
   * considers Lucene's top hits.
   *
   * @param smiles the structure to search for, as SMILES
   * @param chemSearchType {@code EXACT} or {@code SUBSTRUCTURE}
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
      String smiles,
      String chemSearchType,
      String textQuery,
      InventorySearchType resultType,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      Boolean bookable,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);
}
