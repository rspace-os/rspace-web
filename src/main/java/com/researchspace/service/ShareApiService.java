package com.researchspace.service;

import com.researchspace.api.v1.controller.ApiGenericSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.api.v1.model.SharePermissionUpdate;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.model.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;

/**
 * Service layer for Share API operations, handling business logic and providing transactional
 * boundaries for sharing operations.
 */
public interface ShareApiService {

  /**
   * Shares items according to the provided configuration.
   *
   * @param shareConfig The sharing configuration
   * @param user The user performing the sharing operation
   * @return ApiSharingResult containing shared items and any failures
   * @throws BindException if validation errors occur
   */
  @Transactional
  ApiSharingResult shareItems(SharePost shareConfig, User user) throws BindException;

  /**
   * Deletes/unshares a previously shared item.
   *
   * @param id The ID of the share to delete
   * @param user The user performing the deletion
   */
  @Transactional
  void deleteShare(Long id, User user);

  /**
   * Updates permissions on an existing share.
   *
   * @param permissionUpdate The new share permission
   * @param user The user performing the update
   * @throws BindException if validation errors occur
   */
  @Transactional
  void updateShare(SharePermissionUpdate permissionUpdate, User user) throws BindException;

  /**
   * Retrieves a paginated list of shares for a user.
   *
   * @param pgCrit Pagination criteria
   * @param apiSrchConfig Search configuration
   * @param user The user whose shares to retrieve
   * @return ApiShareSearchResult containing the paginated shares
   * @throws BindException if validation errors occur
   */
  @Transactional(readOnly = true)
  ApiShareSearchResult getShares(
      DocumentApiPaginationCriteria pgCrit, ApiGenericSearchConfig apiSrchConfig, User user)
      throws BindException;

  /**
   * Gets all shares for a specific document.
   *
   * @param docId The document ID
   * @param user The requesting user
   * @return List of share information for the document
   */
  @Transactional(readOnly = true)
  DocumentShares getAllSharesForDoc(Long docId, User user);
}
