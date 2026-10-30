package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import java.util.List;
import java.util.Set;

/** For DAO operations on Inventory Container. */
public interface ContainerDao extends GenericDao<Container, Long> {

  /** Returns a database-filtered page under the supplied container read constraint. */
  ResourcePage<Container> getReadableResources(ResourceRequest request, AccessResult access);

  /** Counts containers matching the request and container read constraint. */
  long countReadableResources(ResourceRequest request, AccessResult access);

  /**
   * Get top-level non-deleted containers visible to the current user.
   *
   * @param pgCrit pagination details
   * @param ownedBy (optional) limits results to containers belonging to particular owner
   * @param deletedItemsOption (optional) specifies if deleted containers should be listed
   * @param type (optional) limits results to containers belonging of a particular type. if not
   *     used, all non-workbench containers are returned
   * @param user current user
   */
  ISearchResults<Container> getTopContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      ContainerType type,
      User user);

  /**
   * Gets all containers visible to the particular user.
   *
   * @param pgCrit
   * @param ownedBy (optional) limits results to samples belonging to particular owner
   * @param deletedItemsOption (optional) decide about including deleted samples (excluded by
   *     default)
   * @param user
   * @return
   */
  ISearchResults<Container> getAllContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user);

  /** Get container for given location id. */
  Container getContainerByLocationId(Long id);

  /**
   * Get user's workbench. This method will also create a workbench if none exists yet.
   *
   * @param user
   * @return workbench belonging to the user
   */
  Container getWorkbenchForUser(User user);

  /**
   * Find id of user's workbench. This method will also create a workbench if none exists yet.
   *
   * @param user
   * @return
   */
  Long getWorkbenchIdForUser(User user);

  List<Container> getAllUsingImage(FileProperty fileProperty);

  /** Returns the requested active container IDs that the actor may read. */
  Set<Long> getReadableActiveContainerIds(Set<Long> containerIds, User actor);
}
