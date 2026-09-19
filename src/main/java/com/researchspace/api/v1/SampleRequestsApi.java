/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.SampleRequestApiSearchConfig;
import com.researchspace.api.v1.model.ApiSampleRequest;
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

  /**
   * Requests involving the caller. Serves the requester's own list, the owner's pending queue, and
   * the requests made against a single sample, depending on the search config.
   */
  @GetMapping
  ApiSampleRequestSearchResult getRequestsForUser(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid SampleRequestApiSearchConfig searchConfig,
      BindingResult errors,
      User user)
      throws BindException;

  /** A single request. Visible only to its requester and the requested sample's current owner. */
  @GetMapping(value = "/{id}")
  ApiSampleRequest getRequestById(Long id, User user);

  /**
   * Move a request to a new status. Approve, reject and fulfil are the sample's current owner;
   * cancel is the requester. A reason is required when rejecting and not accepted otherwise.
   */
  @PutMapping(value = "/{id}/status")
  ApiSampleRequest updateStatus(
      Long id,
      @RequestBody @Valid ApiSampleRequestStatusPut status,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleRequest createRequest(
      @RequestBody @Valid ApiSampleRequestPost request, BindingResult errors, User user)
      throws BindException;
}
