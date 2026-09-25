package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import java.util.List;
import java.util.Set;

/** For DAO operations on Inventory SampleRequest. */
public interface SampleRequestDao extends GenericDao<SampleRequest, Long> {

  /**
   * Loads a request for update, taking a row lock so concurrent transitions serialise. The second
   * caller then sees the committed status and fails the normal transition check.
   */
  SampleRequest getForUpdate(Long id);

  /**
   * Page of requests involving the user, on the given side of the request.
   *
   * @param role REQUESTER for requests the user raised, OWNER for requests against samples the user
   *     currently owns; null means either role
   * @param statuses optional status filter; a request matches if its status is any of these. Null
   *     or empty means no status filtering.
   * @param sampleId optional filter to requests against one sample
   */
  ISearchResults<SampleRequest> getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user);

  /**
   * Every PENDING or APPROVED request against one sample, regardless of who raised or currently
   * owns it. Used to cascade-close outstanding requests when a sample's ownership changes.
   */
  List<SampleRequest> getActiveRequestsForSample(Long sampleId);
}
