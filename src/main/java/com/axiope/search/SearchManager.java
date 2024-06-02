package com.axiope.search;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;

/** Top-level search interface */
public interface SearchManager {

  /**
   * Main search method for all types of search, using the WorkspaceListingConfig configuration
   * elements to set the type of search. <br>
   * It is expected that input validation has previously been performed using {@link
   * WorkspaceSearchInputValidator}.
   *
   * @param config A {@link WorkspaceListingConfig} which is assumed to have been validated
   * @param user The authenticated user
   * @return SearchResultEntry
   * @throws IllegalArgumentException if search config is invalid.
   * @throws IOException
   * @throws {@link SearchQueryParseException}
   */
  ISearchResults<BaseRecord> searchWorkspaceRecords(WorkspaceListingConfig config, User user)
      throws IOException;

  /**
   * Facade method that handles "simplified" search functionality, as used by chat bots and other
   * places where user is expected to provide just a search term and receive a list of matching
   * Workspace results.
   *
   * @param user the authenticated user (whose Workspace will be searched)
   * @param searchQuery search term
   * @param maxResults (optional) if number of results should be limited
   * @return
   */
  ISearchResults<BaseRecord> searchUserRecordsWithSimpleQuery(
      User user, String searchQuery, Integer maxResults) throws IOException;

  /**
   * General search through inventory records.
   *
   * @param searchQuery search term
   * @param searchType (optional) if records of just one type should be retrieved
   * @param parentOid (optional) if should only retrieve children of a particular container, or
   *     subsamples of a particular sample
   * @param ownedBy (optional) limit results to items belonging to a particular owner
   * @param deletedItemsOption (optional) to include or exclude items depending on their deleted
   *     status (default is exclude deleted)
   * @param pgCrit
   * @param user authenticated user (whose records will be searched)
   * @return
   */
  ApiInventorySearchResult searchInventoryWithSimpleQuery(
      String searchQuery,
      InventorySearchType searchType,
      GlobalIdentifier parentOid,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);
}
