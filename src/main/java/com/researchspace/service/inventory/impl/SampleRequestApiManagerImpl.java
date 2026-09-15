package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SampleDao;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sampleRequestApiManager")
public class SampleRequestApiManagerImpl implements SampleRequestApiManager {

  private @Autowired SampleRequestDao sampleRequestDao;
  private @Autowired SampleDao sampleDao;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired MessageSourceUtils messages;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissions;

  private enum Actor {
    OWNER,
    REQUESTER
  }

  private record Transition(
      SampleRequestStatus to, SampleRequestStatus from, Actor actor, boolean reasonRequired) {}

  /** The whole state machine. PENDING is absent: it is written only when a request is raised. */
  private static final List<Transition> TRANSITIONS =
      List.of(
          new Transition(
              SampleRequestStatus.APPROVED, SampleRequestStatus.PENDING, Actor.OWNER, false),
          new Transition(
              SampleRequestStatus.REJECTED, SampleRequestStatus.PENDING, Actor.OWNER, true),
          new Transition(
              SampleRequestStatus.CANCELLED, SampleRequestStatus.PENDING, Actor.REQUESTER, false),
          new Transition(
              SampleRequestStatus.FULFILLED, SampleRequestStatus.APPROVED, Actor.OWNER, false));

  @Override
  public ApiSampleRequest createRequest(ApiSampleRequestPost post, User user) {
    assertSampleRequestsEnabled(user);
    Sample sample = readRequestedSample(post.getSampleGlobalId());
    assertNotOwnSample(sample, user);
    assertRequestable(sample);
    SampleRequest request = new SampleRequest(sample, user, post.getNote());
    request.recordStatus(user, SampleRequestStatus.PENDING, null);
    return toDetail(sampleRequestDao.save(request), user);
  }

  @Override
  public ApiSampleRequestSearchResult getRequestsForUser(
      PaginationCriteria<SampleRequest> pgCrit,
      SampleRequestRole role,
      Set<SampleRequestStatus> statuses,
      Long sampleId,
      User user) {

    assertSampleRequestsEnabled(user);
    ISearchResults<SampleRequest> dbRequests =
        sampleRequestDao.getRequestsForUser(pgCrit, role, statuses, sampleId, user);

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

  @Override
  public ApiSampleRequest updateStatus(Long id, ApiSampleRequestStatusPut post, User user) {
    assertSampleRequestsEnabled(user);
    SampleRequest request = sampleRequestDao.getSafeNull(id).orElseThrow(() -> requestNotFound(id));
    assertUserIsPartyToRequest(request, user);

    Transition transition = transitionTo(post.getStatus());
    assertPermittedActor(request, user, transition);
    assertLegalFrom(request, transition);
    String reason = validatedReason(post, transition);

    request.recordStatus(user, post.getStatus(), reason);
    return toDetail(sampleRequestDao.save(request), user);
  }

  private void assertPermittedActor(SampleRequest request, User user, Transition transition) {
    User permitted =
        Actor.OWNER.equals(transition.actor())
            ? request.getSample().getOwner()
            : request.getRequester();
    if (!user.equals(permitted)) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.wrongActor");
    }
  }

  private Transition transitionTo(SampleRequestStatus target) {
    return TRANSITIONS.stream()
        .filter(t -> t.to().equals(target))
        .findFirst()
        .orElseThrow(
            () ->
                new ApiRuntimeException(
                    "errors.inventory.sampleRequest.statusNotSettable", target));
  }

  private void assertLegalFrom(SampleRequest request, Transition transition) {
    if (!transition.from().equals(request.getStatus())) {
      throw new ApiRuntimeException(
          "errors.inventory.sampleRequest.illegalTransition", request.getStatus(), transition.to());
    }
  }

  private String validatedReason(ApiSampleRequestStatusPut post, Transition transition) {
    boolean supplied = StringUtils.isNotBlank(post.getReason());
    if (transition.reasonRequired() && !supplied) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.reasonRequired");
    }
    if (!transition.reasonRequired() && supplied) {
      throw new ApiRuntimeException(
          "errors.inventory.sampleRequest.reasonNotAllowed", post.getStatus());
    }
    return supplied ? post.getReason() : null;
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

  /**
   * Loads the requested sample, rejecting a global id that is well formed but does not name a
   * sample. Read permission is not required: marking a sample requestable is the owner's opt-in to
   * being asked by anyone, and the requestable search is instance-wide, so requestable is the gate
   * rather than readability.
   */
  private Sample readRequestedSample(String sampleGlobalId) {
    GlobalIdentifier oid = new GlobalIdentifier(sampleGlobalId);
    if (!GlobalIdPrefix.SA.equals(oid.getPrefix())) {
      throw new ApiRuntimeException("errors.inventory.globalId.unsupportedType", oid.getIdString());
    }
    return sampleDao
        .getSafeNull(oid.getDbId())
        .orElseThrow(
            () ->
                new NotFoundException(
                    messages.getResourceNotFoundMessage("Sample", oid.getDbId())));
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

  /** Only a sample its owner has published for requests may be asked for. */
  private void assertRequestable(Sample sample) {
    if (!sample.isRequestable()) {
      throw new ApiRuntimeException(
          "errors.inventory.sampleRequest.notRequestable", sample.getOid().getIdString());
    }
  }

  /** An owner already has the material, and would otherwise be approving their own request. */
  private void assertNotOwnSample(Sample sample, User user) {
    if (user.equals(sample.getOwner())) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.ownSample");
    }
  }
}
