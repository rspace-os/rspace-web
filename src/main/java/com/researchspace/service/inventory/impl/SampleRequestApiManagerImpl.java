package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SampleRequestDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SampleRequestApiManager;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sampleRequestApiManager")
public class SampleRequestApiManagerImpl implements SampleRequestApiManager {

  private @Autowired SampleRequestDao sampleRequestDao;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired MessageSourceUtils messages;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissions;

  @Override
  public ApiSampleRequest createRequest(ApiSampleRequestPost post, User user) {
    assertSampleRequestsEnabled(user);
    Sample sample = readRequestedSample(post.getSampleGlobalId(), user);
    assertNotOwnSample(sample, user);
    assertRequestable(sample);
    SampleRequest saved = sampleRequestDao.save(new SampleRequest(sample, user, post.getNote()));
    return toDetail(saved, user);
  }

  @Override
  public ApiSampleRequestSearchResult getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      SampleRequestStatus status,
      Long sampleId,
      User user) {

    assertSampleRequestsEnabled(user);
    ISearchResults<SampleRequest> dbRequests =
        sampleRequestDao.getRequestsForUser(pgCrit, role, status, sampleId, user);

    List<ApiSampleRequestInfo> requests =
        dbRequests.getResults().stream()
            .map(request -> toInfo(request, user))
            .collect(Collectors.toList());

    ApiSampleRequestSearchResult apiSearchResult = new ApiSampleRequestSearchResult();
    apiSearchResult.setTotalHits(dbRequests.getTotalHits());
    apiSearchResult.setPageNumber(dbRequests.getPageNumber());
    apiSearchResult.setItems(requests);
    return apiSearchResult;
  }

  @Override
  public ApiSampleRequest getRequestById(Long id, User user) {
    assertSampleRequestsEnabled(user);
    SampleRequest request = sampleRequestDao.getSafeNull(id).orElseThrow(() -> requestNotFound(id));
    assertUserIsPartyToRequest(request, user);
    return toDetail(request, user);
  }

  private ApiSampleRequestInfo toInfo(SampleRequest request, User user) {
    return applyOutgoingSampleRules(new ApiSampleRequestInfo(request), request, user);
  }

  private ApiSampleRequest toDetail(SampleRequest request, User user) {
    return applyOutgoingSampleRules(new ApiSampleRequest(request), request, user);
  }

  /**
   * Runs the embedded sample through the standard outgoing-record hook, so a limited-read caller
   * sees the same blanked sample they would get from the samples endpoints, and permittedActions is
   * populated.
   */
  private <T extends ApiSampleRequestInfo> T applyOutgoingSampleRules(
      T apiRequest, SampleRequest request, User user) {
    sampleApiManager.setOtherFieldsForOutgoingApiInventoryRecord(
        apiRequest.getSample(), request.getSample(), user);
    return apiRequest;
  }

  /** Rejects a global id that is well formed but does not name a sample. */
  private Sample readRequestedSample(String sampleGlobalId, User user) {
    GlobalIdentifier oid = new GlobalIdentifier(sampleGlobalId);
    if (!GlobalIdPrefix.SA.equals(oid.getPrefix())) {
      throw new ApiRuntimeException("errors.inventory.globalId.unsupportedType", oid.getIdString());
    }
    return sampleApiManager.assertUserCanReadSample(oid.getDbId(), user);
  }

  /** Only the requester and the sample's current owner may see a request. */
  private void assertUserIsPartyToRequest(SampleRequest request, User user) {
    if (!user.equals(request.getRequester()) && !user.equals(request.getSample().getOwner())) {
      throw requestNotFound(request.getId());
    }
  }

  /** Not-found rather than forbidden, so a request's existence is not disclosed. */
  private NotFoundException requestNotFound(Long id) {
    return new NotFoundException(messages.getResourceNotFoundMessage("Sample request", id));
  }

  private void assertSampleRequestsEnabled(User user) {
    if (!systemPropertyPermissions.isPropertyAllowed(
        user, SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE)) {
      throw new UnsupportedOperationException(
          messages.getMessage("errors.inventory.sampleRequest.notEnabled"));
    }
  }

  /**
   * TODO RSDEV-1365: every readable sample is treated as requestable until Sample.requestable
   * lands. Replace the body with a check on sample.isRequestable().
   */
  private void assertRequestable(Sample sample) {
    // FIXME RSDEV-1365: no-op placeholder, see javadoc
  }

  /** An owner already has the material, and would otherwise be approving their own request. */
  private void assertNotOwnSample(Sample sample, User user) {
    if (user.equals(sample.getOwner())) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.ownSample");
    }
  }
}
