package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import javax.ws.rs.NotFoundException;

/** Handles API actions around Inventory Container. */
public interface ContainerApiManager extends InventoryApiManager {

  /**
   * Get all top-level not-deleted containers that user can see. Optionally limit to containers
   * belonging to a particular owner.
   */
  ISearchResults<ApiContainerInfo> getTopContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /** Checks if container with given id exists */
  boolean exists(long id);

  /**
   * Checks if container with given id is readable by the user.
   *
   * @return The container - if readable (without lazy-loaded properties)
   * @throws NotFoundException if user cannot read the container
   */
  Container assertUserCanReadContainer(Long id, User user);

  /**
   * Checks if container with given id is editable by the user.
   *
   * @return The container if editable (without lazy-loaded properties)
   * @throws NotFoundException if user cannot read the container, or IllegalArgumentException if
   *     they cannot edit it
   */
  Container assertUserCanEditContainer(Long id, User user);

  /**
   * Checks if container with given id can be deleted/restored by the user.
   *
   * @return The container, if deletable (without lazy-loaded properties)
   * @throws NotFoundException if user cannot read the container, or IllegalArgumentException if
   *     they cannot restore it
   */
  Container assertUserCanDeleteContainer(Long id, User user);

  /**
   * Checks if container with given id is transferrable by the user.
   *
   * @return The container if editable (without lazy-loaded properties)
   * @throws NotFoundException if user cannot read the container, or IllegalArgumentException if
   *     they cannot transfer it
   */
  Container assertUserCanTransferContainer(Long id, User user);

  /**
   * Retrieves fully populated non-workbench container for API GET call, including the content.
   * Causes side-effects like READ entry in audit trail.
   *
   * @return ApiContainer with a given id
   * @throws NotFoundException if Container with a given id doesn't exist, or
   *     IllegalArgumentException if target container is a workbench
   */
  ApiContainer getApiContainerById(Long id, User user);

  /**
   * Retrieves fully populated non-workbench container for API GET call, with or without the content
   * depending on 'includeContent' parameter. Causes side-effects like READ entry in audit trail.
   *
   * @return ApiContainer with a given id
   * @throws NotFoundException if Container with a given id doesn't exist, or
   *     IllegalArgumentException if target container is a workbench
   */
  ApiContainer getApiContainerById(Long id, boolean includeContent, User user);

  /**
   * Retrieves fully populated workbench for API GET call, including the content. Causes
   * side-effects like READ entry in audit trail.
   *
   * @return ApiContainer with a given id
   * @throws NotFoundException if Container with a given id doesn't exist, or
   *     IllegalArgumentException if target container is not a workbench
   */
  ApiContainer getApiWorkbenchById(Long id, User user);

  /**
   * Retrieves fully populated workbench for API GET call, with or without the content depending on
   * 'includeContent' parameter. Causes side-effects like READ entry in audit trail.
   *
   * @return ApiContainer with a given id
   * @throws NotFoundException if Container with a given id doesn't exist, or
   *     IllegalArgumentException if target container is not a workbench
   */
  ApiContainer getApiWorkbenchById(Long id, boolean includeContent, User user);

  /**
   * For internal retrieval of a ApiContainer object. Doesn't cause side effects like READ entry in
   * audit trail.
   *
   * @return ApiContainer with a given id
   */
  ApiContainer getApiContainerIfExists(Long id, User user);

  /**
   * For internal retrieval of a Container object. Doesn't cause side effects like READ entry in
   * audit trail.
   *
   * @return lazy-initialized Container with a given id
   * @throws NotFoundException if Container with a given id doesn't exist
   */
  Container getContainerById(Long id, User user);

  /**
   * Returns list of records info in the content as search results, sorted and filtered according to
   * ownedBy, searchType and pagination criteria.
   *
   * @return list of children of the container with a given id
   */
  ApiInventorySearchResult searchForContentOfContainer(
      Long parentId,
      String ownedBy,
      InventorySearchType searchType,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);

  /**
   * Saves incoming ApiContainer in a database as a new container.
   *
   * @returns created container
   */
  ApiContainer createNewApiContainer(ApiContainer newContainer, User user);

  /**
   * Updates database container based on apiContainer provided.
   *
   * @returns updated container
   */
  ApiContainer updateApiContainer(ApiContainer apiContainer, User user);

  /**
   * Moves container outside of its current location but doesn't set a new location. As a result the
   * container becomes top-level one i.e. with a null parentLocation/parentContainer.
   *
   * <p>Method intended for internal use, client will rather call general {@link
   * #updateApiContainer(ApiContainer, User)} or bulk move endpoint.
   *
   * @param containerId
   * @param user moving container
   * @return
   */
  ApiContainer moveContainerToTopLevel(Long containerId, User user);

  /**
   * @return container with updated owner field
   */
  ApiContainer changeApiContainerOwner(ApiContainer apiContainer, User user);

  /**
   * Mark container as deleted - it will no longer be included in standard listings.
   *
   * @return deleted container
   */
  ApiContainer markContainerAsDeleted(Long containerId, User user);

  /**
   * Un-deletes the container and moves it to current user's bench.
   *
   * @return restored container
   */
  ApiContainer restoreDeletedContainer(Long containerId, User user);

  ApiContainer duplicate(Long containerId, User user);

  /**
   * Get all workbenches that user can see. Optionally limit to a workbench belonging to a
   * particular owner.
   */
  ISearchResults<ApiContainerInfo> getWorkbenchesForUser(
      PaginationCriteria<Container> pgCrit, String ownedBy, User user);

  /**
   * Find id of a workbench container of the user
   *
   * @param user
   * @return
   */
  Long getWorkbenchIdForUser(User user);
}
