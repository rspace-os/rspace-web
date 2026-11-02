/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.SampleRequestApiPaginationCriteria;
import com.researchspace.api.v1.controller.SampleRequestApiSearchConfig;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestFulfilByTransferPut;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/inventory/v1/sampleRequests")
public interface SampleRequestsApi {

  /** Requests involving the caller, optionally filtered by role, status and sample. */
  @GetMapping
  ApiSampleRequestSearchResult getRequestsForUser(
      @Valid SampleRequestApiPaginationCriteria pgCrit,
      @Valid SampleRequestApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * A single request. Visible only to its requester, the sample's current owner, and whoever owned
   * it when the request was raised.
   */
  @GetMapping(value = "/{id}")
  ApiSampleRequest getRequestById(Long id, User user);

  /**
   * Move a request to a new status: approve, reject and fulfil by the sample's current owner,
   * cancel by the requester. A reason is required when rejecting and not accepted otherwise. When
   * fulfilling, a transferredSampleGlobalId may name the sample the request was fulfilled with; it
   * is only recorded, not transferred.
   */
  @PutMapping(value = "/{id}/status")
  ApiSampleRequest updateStatus(
      Long id,
      @RequestBody @Valid ApiSampleRequestStatusPut status,
      BindingResult errors,
      User user)
      throws BindException;

  /**
   * Fulfil a request by transferring a sample to the requester, in one step: the requested sample,
   * or the one named by sampleToTransferGlobalId. Only the requested sample's current owner may do
   * this.
   */
  @PutMapping(value = "/{id}/actions/fulfilByTransfer")
  ApiSampleRequest fulfilByTransfer(
      Long id,
      @RequestBody(required = false) @Valid ApiSampleRequestFulfilByTransferPut transfer,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleRequest createRequest(
      @RequestBody @Valid ApiSampleRequestPost request, BindingResult errors, User user)
      throws BindException;
}
