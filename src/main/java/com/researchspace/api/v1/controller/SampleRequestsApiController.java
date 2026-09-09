package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.SampleRequestsApi;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.service.inventory.SampleRequestApiManager;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class SampleRequestsApiController extends BaseApiInventoryController
    implements SampleRequestsApi {

  @Autowired private SampleRequestApiManager sampleRequestMgr;

  @Override
  public ApiSampleRequestSearchResult getRequestsForUser(
      @Valid InventoryApiPaginationCriteria apiPgCrit,
      @Valid SampleRequestApiSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);
    if (apiPgCrit == null) {
      apiPgCrit = new InventoryApiPaginationCriteria();
    }
    if (srchConfig == null) {
      srchConfig = new SampleRequestApiSearchConfig();
    }
    PaginationCriteria<SampleRequest> pgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, SampleRequest.class);

    ApiSampleRequestSearchResult result =
        sampleRequestMgr.getRequestsForUser(
            pgCrit,
            srchConfig.getRoleAsEnum(),
            srchConfig.getStatusAsEnum(),
            srchConfig.getSampleId(),
            user);
    result.getRequests().forEach(this::buildAndAddSelfLink);
    result.addNavigationLinks(getInventoryApiBaseURIBuilder(), apiPgCrit, srchConfig);
    return result;
  }

  @Override
  public ApiSampleRequest getRequestById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiSampleRequest request = sampleRequestMgr.getRequestById(id, user);
    buildAndAddSelfLink(request);
    return request;
  }

  private void buildAndAddSelfLink(ApiSampleRequestInfo request) {
    request.buildAndAddSelfLink(
        SAMPLE_REQUESTS_ENDPOINT, String.valueOf(request.getId()), getInventoryApiBaseURIBuilder());
  }

  @Override
  public ApiSampleRequest createRequest(
      @RequestBody @Valid ApiSampleRequestPost request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);
    ApiSampleRequest created = sampleRequestMgr.createRequest(request, user);
    buildAndAddSelfLink(created);
    return created;
  }
}
