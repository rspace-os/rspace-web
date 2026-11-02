package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import java.util.Set;

/** Handles requests for material from Inventory samples. */
public interface SampleRequestApiManager {

  /**
   * Raise a request against a requestable sample, in PENDING state.
   *
   * @param post the sample's global id and the requester's note
   * @param user the requester
   * @return the created request
   */
  ApiSampleRequest createRequest(ApiSampleRequestPost post, User user);

  /**
   * A single request, visible only to its requester, the sample's current owner, and whoever owned
   * it when the request was raised.
   *
   * @throws jakarta.ws.rs.NotFoundException if absent, or the user is neither party
   */
  ApiSampleRequest getRequestById(Long id, User user);

  /**
   * Move a request to a new status, recording who did it and why in the request's history.
   *
   * @throws jakarta.ws.rs.NotFoundException if absent, or the user is neither party
   * @throws com.researchspace.api.v1.auth.ApiRuntimeException if the user is not the permitted
   *     actor, the transition is not legal, or the reason is missing or not allowed
   */
  ApiSampleRequest updateStatus(Long id, ApiSampleRequestStatusPut post, User user);

  /**
   * Fulfil a request and transfer a sample to its requester, as one change: the given sample, or
   * the requested one when none is given. Only the requested sample's current owner may do this.
   *
   * @throws jakarta.ws.rs.NotFoundException if absent, the user is neither party, or the sample is
   *     missing, in the trash or not fully readable by the user
   * @throws com.researchspace.api.v1.auth.ApiRuntimeException if the user is not the permitted
   *     actor, the request is no longer open, the global id does not name a sample, the requester's
   *     account was deleted, or the requester already owns the sample
   * @throws IllegalArgumentException if the user may not transfer the sample
   * @throws InventoryEditLockHeldException if another user is editing the sample
   */
  ApiSampleRequest fulfilByTransfer(Long id, String sampleToTransferGlobalId, User user);

  /**
   * Page of requests involving the user, on the given side of the request.
   *
   * @param role REQUESTER for requests the user raised, OWNER for requests they received as the
   *     sample's owner when each was raised; null means either role
   * @param statuses optional status filter; a request matches if its status is any of these. Null
   *     or empty means no status filtering.
   * @param sampleId optional filter to requests against one sample
   */
  ApiSampleRequestSearchResult getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user);

  /**
   * Closes every PENDING or APPROVED request against a sample whose ownership is being transferred:
   * the new owner's own requests are fulfilled with it, all others are rejected. The caller must
   * have checked that the actor may transfer the sample: by now its owner has already changed.
   *
   * @param sampleId the sample whose active requests should be closed
   * @param actor attributed as who made the change; the person who performed the transfer
   * @param newOwner whose own requests are fulfilled
   */
  void closeActiveRequestsForTransferredSample(Long sampleId, User actor, User newOwner);
}
