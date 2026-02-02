package com.researchspace.service;

import com.researchspace.api.v1.controller.ApiShareSearchConfig;
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

  ApiSharingResult shareItems(SharePost shareConfig, User user) throws BindException;

  void deleteShare(Long id, User user);

  @Transactional
  void updateShare(SharePermissionUpdate permissionUpdate, User user) throws BindException;

  ApiShareSearchResult getShares(
      DocumentApiPaginationCriteria pgCrit, ApiShareSearchConfig apiShareSrchConfig, User user)
      throws BindException;

  @Transactional(readOnly = true)
  DocumentShares getAllSharesForDoc(Long docId, User user);
}
