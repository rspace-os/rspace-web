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
import org.springframework.context.ApplicationEventPublisher;

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
   * A single request, visible only to its requester and the current owner of the requested sample.
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
   * Page of requests involving the user, on the given side of the request.
   *
   * @param role REQUESTER for requests the user raised, OWNER for requests against samples the user
   *     currently owns; null means either role
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

  /** Exposed so tests can swap in a mock publisher, as the other inventory managers do. */
  void setPublisher(ApplicationEventPublisher publisher);

  /**
   * Rejects every request currently PENDING or APPROVED against the given sample. Called as a side
   * effect of transferring the sample's ownership: those requests were addressed to whoever owned
   * it before, so they're closed rather than left pointing at a context that no longer applies. Not
   * exposed via the public API itself.
   *
   * @param sampleId the sample whose other active requests should be closed
   * @param actor attributed as who made the change; the person who performed the transfer
   * @param newOwner named in the rejection reason shown against each closed request
   */
  void autoRejectActiveRequestsForTransferredSample(Long sampleId, User actor, User newOwner);
}
