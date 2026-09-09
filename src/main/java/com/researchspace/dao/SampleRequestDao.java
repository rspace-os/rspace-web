package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;

/** For DAO operations on Inventory SampleRequest. */
public interface SampleRequestDao extends GenericDao<SampleRequest, Long> {

  /**
   * Page of requests involving the user, on the given side of the request.
   *
   * @param role REQUESTER for requests the user raised, OWNER for requests against samples the user
   *     currently owns
   * @param status optional status filter
   * @param sampleId optional filter to requests against one sample
   */
  ISearchResults<SampleRequest> getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      SampleRequestStatus status,
      Long sampleId,
      User user);
}
