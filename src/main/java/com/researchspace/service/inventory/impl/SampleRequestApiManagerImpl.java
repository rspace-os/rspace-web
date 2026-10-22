package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusChange;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.SampleRequestDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.events.SampleRequestStatusEvent;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.inventory.SampleRequestStatusChange;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SampleRequestApiManager;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service("sampleRequestApiManager")
public class SampleRequestApiManagerImpl implements SampleRequestApiManager {

  private @Autowired SampleRequestDao sampleRequestDao;
  private @Autowired SampleDao sampleDao;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired MessageSourceUtils messages;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissions;
  private @Autowired UserDao userDao;
  private @Autowired ApplicationEventPublisher publisher;

  @Override
  public void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  private enum Actor {
    CURRENT_OWNER,
    REQUESTER
  }

  private record Transition(
      SampleRequestStatus from, SampleRequestStatus to, Actor actor, boolean reasonRequired) {}

  /** Every transition a user can make; PENDING is only written when a request is raised. */
  private static final List<Transition> TRANSITIONS =
      List.of(
          new Transition(
              SampleRequestStatus.PENDING,
              SampleRequestStatus.APPROVED,
              Actor.CURRENT_OWNER,
              false),
          new Transition(
              SampleRequestStatus.PENDING, SampleRequestStatus.REJECTED, Actor.CURRENT_OWNER, true),
          new Transition(
              SampleRequestStatus.PENDING, SampleRequestStatus.CANCELLED, Actor.REQUESTER, false),
          new Transition(
              SampleRequestStatus.PENDING,
              SampleRequestStatus.FULFILLED,
              Actor.CURRENT_OWNER,
              false),
          new Transition(
              SampleRequestStatus.APPROVED,
              SampleRequestStatus.REJECTED,
              Actor.CURRENT_OWNER,
              true),
          new Transition(
              SampleRequestStatus.APPROVED,
              SampleRequestStatus.FULFILLED,
              Actor.CURRENT_OWNER,
              false));

  @Override
  public ApiSampleRequest createRequest(ApiSampleRequestPost post, User user) {
    GlobalIdentifier oid = sampleOid(post.getSampleGlobalId());
    // share-locked as the first read, so a concurrent transfer cannot leave the new request open;
    // no read permission is needed, requestable is the gate
    Sample locked = sampleDao.getWithSharedLock(oid.getDbId());
    assertSampleRequestsEnabled(user);
    Sample sample = sampleNotInTrashOrThrow(locked, oid);
    assertNotOwnSample(sample, user);
    assertRequestable(sample);
    SampleRequest request = new SampleRequest(sample, user, post.getNote());
    request.recordStatus(user, SampleRequestStatus.PENDING, null);
    SampleRequest saved = sampleRequestDao.save(request);
    publisher.publishEvent(new SampleRequestStatusEvent(saved, user));
    return toDetail(saved, user);
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

    // the same few users appear on most rows, so each is looked up once per page
    Map<Long, ApiUser> usersById = new HashMap<>();
    List<ApiSampleRequestInfo> requests =
        dbRequests.getResults().stream()
            .map(request -> toInfo(request, user, usersById))
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
    // locked as the transaction's first read, so concurrent transitions serialise
    SampleRequest request = sampleRequestDao.getForUpdate(id);
    assertSampleRequestsEnabled(user);
    if (request == null) {
      throw requestNotFound(id);
    }
    assertUserIsPartyToRequest(request, user);

    List<Transition> toTarget = transitionsToSettableStatusOrThrow(post.getStatus());
    // every transition to one target shares its actor, so who may act is checked first
    assertPermittedActor(request, user, toTarget.get(0).actor());
    Transition transition = legalTransitionFromOrThrow(toTarget, request.getStatus());
    String reason = validatedReason(post, transition);
    Sample transferredSample = resolveTransferredSample(post, user);

    request.recordStatus(user, post.getStatus(), reason, transferredSample);
    SampleRequest saved = sampleRequestDao.save(request);
    publisher.publishEvent(new SampleRequestStatusEvent(saved, user));
    return toDetail(saved, user);
  }

  private void assertPermittedActor(SampleRequest request, User user, Actor actor) {
    boolean permitted =
        Actor.CURRENT_OWNER.equals(actor)
            ? user.getUsername().equals(request.getSample().getOwner().getUsername())
            : request.isRequester(user);
    if (!permitted) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.wrongActor");
    }
  }

  /** Every transition to the target; a target with none is never settable. */
  private List<Transition> transitionsToSettableStatusOrThrow(SampleRequestStatus target) {
    List<Transition> toTarget = TRANSITIONS.stream().filter(t -> t.to().equals(target)).toList();
    if (toTarget.isEmpty()) {
      throw new ApiRuntimeException("errors.inventory.sampleRequest.statusNotSettable", target);
    }
    return toTarget;
  }

  /** A target may be reachable from several statuses, e.g. FULFILLED from PENDING or APPROVED. */
  private Transition legalTransitionFromOrThrow(
      List<Transition> toTarget, SampleRequestStatus current) {
    return toTarget.stream()
        .filter(t -> t.from().equals(current))
        .findFirst()
        .orElseThrow(
            () ->
                new ApiRuntimeException(
                    "errors.inventory.sampleRequest.illegalTransition",
                    current,
                    toTarget.get(0).to()));
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

  private ApiSampleRequestInfo toInfo(
      SampleRequest request, User user, Map<Long, ApiUser> usersById) {
    ApiSampleRequestInfo info = new ApiSampleRequestInfo(request);
    info.setRequester(
        usersById.computeIfAbsent(
            request.getRequesterId(), id -> toApiUser(request.getRequesterUsername(), id)));
    info.setOriginalOwner(
        usersById.computeIfAbsent(
            request.getOriginalOwnerId(), id -> toApiUser(request.getOriginalOwner(), id)));
    return applyOutgoingSampleRules(info, request, user);
  }

  private ApiSampleRequest toDetail(SampleRequest request, User user) {
    ApiSampleRequest detail = new ApiSampleRequest(request);
    detail.setRequester(toApiUser(request.getRequesterUsername(), request.getRequesterId()));
    detail.setOriginalOwner(toApiUser(request.getOriginalOwner(), request.getOriginalOwnerId()));
    for (int i = 0; i < detail.getStatusChanges().size(); i++) {
      ApiSampleRequestStatusChange apiChange = detail.getStatusChanges().get(i);
      SampleRequestStatusChange change = request.getStatusChanges().get(i);
      apiChange.setCreatedBy(toApiUser(change.getCreatedByUsername(), change.getCreatedById()));
      if (change.getTransferredSample() != null) {
        sampleApiManager.setOtherFieldsForOutgoingApiInventoryRecord(
            apiChange.getTransferredSample(), change.getTransferredSample(), user);
      }
    }
    return applyOutgoingSampleRules(detail, request, user);
  }

  /** A deleted user is shown by the username kept in the record, without id or name. */
  private ApiUser toApiUser(String username, Long userId) {
    return userDao
        .getSafeNull(userId)
        .map(found -> new ApiUser(found, true))
        .orElseGet(
            () -> {
              ApiUser deleted = new ApiUser();
              deleted.setUsername(username);
              return deleted;
            });
  }

  /**
   * Runs the embedded sample through the standard outgoing-record hook, so a caller who cannot
   * fully read it sees the same reduced view as from the samples endpoints, and permittedActions is
   * populated.
   */
  private <T extends ApiSampleRequestInfo> T applyOutgoingSampleRules(
      T apiRequest, SampleRequest request, User user) {
    sampleApiManager.setOtherFieldsForOutgoingApiInventoryRecord(
        apiRequest.getSample(), request.getSample(), user);
    return apiRequest;
  }

  /** A missing sample and one in the trash are both not found. */
  private Sample sampleNotInTrashOrThrow(Sample sample, GlobalIdentifier oid) {
    if (sample == null || sample.isDeleted()) {
      throw new NotFoundException(messages.getResourceNotFoundMessage("Sample", oid.getDbId()));
    }
    return sample;
  }

  /** Rejects a global id that is malformed or names something other than a sample. */
  private GlobalIdentifier sampleOid(String globalId) {
    GlobalIdentifier oid =
        GlobalIdentifier.isValid(globalId) ? new GlobalIdentifier(globalId) : null;
    if (oid == null || !GlobalIdPrefix.SA.equals(oid.getPrefix())) {
      throw new ApiRuntimeException("errors.inventory.globalId.unsupportedType", globalId);
    }
    return oid;
  }

  /**
   * Absent when none was supplied; accepted only when fulfilling, for a sample the fulfiller can
   * fully read.
   */
  private Sample resolveTransferredSample(ApiSampleRequestStatusPut post, User user) {
    String globalId = post.getTransferredSampleGlobalId();
    if (StringUtils.isBlank(globalId)) {
      return null;
    }
    if (post.getStatus() != SampleRequestStatus.FULFILLED) {
      throw new ApiRuntimeException(
          "errors.inventory.sampleRequest.transferredSampleNotAllowed", post.getStatus());
    }
    GlobalIdentifier oid = sampleOid(globalId);
    Sample sample = sampleNotInTrashOrThrow(sampleDao.getSafeNull(oid.getDbId()).orElse(null), oid);
    if (!invPermissions.canUserReadInventoryRecord(sample, user)) {
      throw new NotFoundException(messages.getResourceNotFoundMessage("Sample", sample.getId()));
    }
    return sample;
  }

  @Override
  public void closeActiveRequestsForTransferredSample(Long sampleId, User actor, User newOwner) {
    String rejectedReason =
        messages.getMessage(
            "inventory.sampleRequest.autoRejectedReason", new Object[] {newOwner.getFullName()});
    String fulfilledReason = messages.getMessage("inventory.sampleRequest.autoFulfilledReason");
    for (SampleRequest request : sampleRequestDao.getActiveRequestsForSample(sampleId)) {
      boolean toRequester = request.isRequester(newOwner);
      SampleRequestStatus target =
          toRequester ? SampleRequestStatus.FULFILLED : SampleRequestStatus.REJECTED;
      // a PI or admin of the owner's group may transfer too, so only legality is checked
      legalTransitionFromOrThrow(transitionsToSettableStatusOrThrow(target), request.getStatus());
      if (toRequester) {
        request.recordStatus(actor, target, fulfilledReason, request.getSample());
      } else {
        request.recordStatus(actor, target, rejectedReason);
      }
      SampleRequest saved = sampleRequestDao.save(request);
      publisher.publishEvent(new SampleRequestStatusEvent(saved, actor));
    }
  }

  /**
   * The requester, the sample's current owner, and whoever owned it when the request was raised may
   * see a request.
   */
  private void assertUserIsPartyToRequest(SampleRequest request, User user) {
    if (!request.isRequester(user)
        && !request.isOriginalOwner(user)
        && !user.equals(request.getSample().getOwner())) {
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
   * Only a sample marked requestable may be asked for, and only while sample requests are on for
   * its owner too: a community that switches them off must not receive requests it cannot act on.
   */
  private void assertRequestable(Sample sample) {
    if (!sample.isRequestable()
        || !systemPropertyPermissions.isPropertyAllowed(
            sample.getOwner(), SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE)) {
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
