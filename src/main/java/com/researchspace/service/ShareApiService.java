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

public interface ShareApiService {

  @Transactional
  ApiSharingResult shareItems(SharePost shareConfig, User user) throws BindException;

  @Transactional
  void deleteShare(Long id, User user);

  @Transactional
  void updateShare(SharePermissionUpdate permissionUpdate, User user) throws BindException;

  @Transactional(readOnly = true)
  ApiShareSearchResult getShares(
      DocumentApiPaginationCriteria pgCrit, ApiGenericSearchConfig apiSrchConfig, User user)
      throws BindException;

  @Transactional(readOnly = true)
  DocumentShares getAllSharesForDoc(Long docId, User user);
}
