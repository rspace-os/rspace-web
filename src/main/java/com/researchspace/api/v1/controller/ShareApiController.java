package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ShareApi;
import com.researchspace.api.v1.model.ApiShareInfo;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.model.User;
import com.researchspace.service.ShareApiService;
import java.util.List;
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
  public ApiSharingResult updateShare(
      @PathVariable("id") Long id,
      @Valid @RequestBody SharePost shareConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return shareApiService.updateShare(id, shareConfig, user);
  }

  @Override
  public ApiShareSearchResult getShares(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiGenericSearchConfig apiSrchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    return shareApiService.getShares(pgCrit, apiSrchConfig, user);
  }

  @Override
  public List<ApiShareInfo> getAllSharesForDoc(Long docId, User user) {
    return shareApiService.getAllSharesForDoc(docId, user);
  }
}
