package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ShareApi;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.api.v1.model.SharePermissionUpdate;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.model.User;
import com.researchspace.service.ShareApiService;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class ShareApiController extends BaseApiController implements ShareApi {

  @Autowired private ShareApiService shareApiService;

  @Override
  public ApiSharingResult shareItems(
      @Valid @RequestBody SharePost shareConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return shareApiService.shareItems(shareConfig, user);
  }

  @Override
  public void deleteShare(@PathVariable("id") Long id, @RequestAttribute(name = "user") User user) {
    shareApiService.deleteShare(id, user);
  }

  @Override
  public void updateShare(
      @Valid @RequestBody SharePermissionUpdate permissionUpdate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    shareApiService.updateShare(permissionUpdate, user);
  }

  @Override
  public ApiShareSearchResult getShares(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiShareSearchConfig apiShareSrchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return shareApiService.getShares(pgCrit, apiShareSrchConfig, user);
  }

  @Override
  public DocumentShares getAllSharesForDoc(
      @PathVariable("id") Long docId, @RequestAttribute(name = "user") User user) {
    return shareApiService.getAllSharesForDoc(docId, user);
  }
}
