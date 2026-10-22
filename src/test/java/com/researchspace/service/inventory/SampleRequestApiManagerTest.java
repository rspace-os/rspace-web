package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusChange;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.SampleRequestStatusEvent;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class SampleRequestApiManagerTest extends SpringTransactionalTest {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;
  private @Autowired ApplicationEventPublisher realPublisher;

  private User owner;
  private User requester;
  private ApiSampleWithFullSubSamples sample;
  private String originalSampleRequestsAvailable;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    requester = createAndSaveAPi();
    owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    initialiseContentWithEmptyContent(requester, owner);
    createGroupForUsers(requester, requester.getUsername(), "", requester, owner);

    // the manager caches the value, and the cache survives this test's rollback
    originalSampleRequestsAvailable =
        systemPropertyMgr.findByName(SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE).getValue();
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
    sample = createBasicSampleForUser(owner);
    markRequestable(sample);
  }

  @AfterEach
  public void restorePublisherAndSetting() {
    sampleRequestApiMgr.setPublisher(realPublisher);
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        originalSampleRequestsAvailable,
        getSysAdminUser());
  }

  @Test
  public void createRequest_publishesStatusEventForAuditing() {
    ApplicationEventPublisher mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleRequestApiMgr.setPublisher(mockPublisher);

    ApiSampleRequest created = raiseRequest("Need 2ml for the binding assay");

    ArgumentCaptor<SampleRequestStatusEvent> published =
        ArgumentCaptor.forClass(SampleRequestStatusEvent.class);
    Mockito.verify(mockPublisher).publishEvent(published.capture());
    assertEquals(created.getId(), published.getValue().getRequest().getId());
    assertEquals(SampleRequestStatus.PENDING, published.getValue().getRequest().getStatus());
    assertEquals(requester, published.getValue().getActor());
  }

  @Test
  public void updateStatus_publishesStatusEventForAuditing() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    // installed after raising, so only the transition's event is captured
    ApplicationEventPublisher mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleRequestApiMgr.setPublisher(mockPublisher);

    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    ArgumentCaptor<SampleRequestStatusEvent> published =
        ArgumentCaptor.forClass(SampleRequestStatusEvent.class);
    Mockito.verify(mockPublisher).publishEvent(published.capture());
    assertEquals(raised.getId(), published.getValue().getRequest().getId());
    assertEquals(SampleRequestStatus.APPROVED, published.getValue().getRequest().getStatus());
    assertEquals(owner, published.getValue().getActor());
  }

  @Test
  public void transfer_publishesAStatusEventForEachRequestItCloses() {
    ApiSampleRequest recipients = raiseRequest("Need 2ml for the binding assay");
    User otherRequester = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    ApiSampleRequest others = raiseRequestAs(otherRequester);
    ApplicationEventPublisher mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleRequestApiMgr.setPublisher(mockPublisher);

    transferSample(requester, owner);

    ArgumentCaptor<SampleRequestStatusEvent> published =
        ArgumentCaptor.forClass(SampleRequestStatusEvent.class);
    Mockito.verify(mockPublisher, Mockito.times(2)).publishEvent(published.capture());
    Map<Long, SampleRequestStatus> statusById =
        published.getAllValues().stream()
            .collect(
                Collectors.toMap(e -> e.getRequest().getId(), e -> e.getRequest().getStatus()));
    assertEquals(
        Map.of(
            recipients.getId(), SampleRequestStatus.FULFILLED,
            others.getId(), SampleRequestStatus.REJECTED),
        statusById);
    published.getAllValues().forEach(event -> assertEquals(owner, event.getActor()));
  }

  @Test
  public void refusedTransitionPublishesNothingToAudit() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    ApplicationEventPublisher mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleRequestApiMgr.setPublisher(mockPublisher);

    // the requester may not approve their own request
    assertThrows(
        ApiRuntimeException.class,
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), requester));

    Mockito.verifyNoInteractions(mockPublisher);
  }

  @Test
  public void createRequest_persistsPendingRequestAgainstSample() {
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("Need 2ml for the binding assay");

    ApiSampleRequest created = sampleRequestApiMgr.createRequest(post, requester);

    assertNotNull(created.getId());
    assertEquals(SampleRequestStatus.PENDING, created.getStatus());
    assertEquals("Need 2ml for the binding assay", created.getNote());
    assertEquals(sample.getGlobalId(), created.getSample().getGlobalId());
    assertEquals(requester.getUsername(), created.getRequester().getUsername());
    assertEquals(owner.getUsername(), created.getOriginalOwner().getUsername());
  }

  @Test
  public void getRequestsForUser_ownerRole_usesOriginalOwnerRatherThanTheSamplesCurrentOwner() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));

    transferSample(newOwner, owner);

    // the original owner still sees the request under "Received": they were the one asked,
    // regardless of who owns the sample now
    ApiSampleRequestSearchResult originalOwnersView = listFor(SampleRequestRole.OWNER, owner);
    assertEquals(1L, originalOwnersView.getTotalHits().longValue());
    assertEquals(raised.getId(), originalOwnersView.getRequests().get(0).getId());

    // the sample's new owner does not: they were never asked for anything
    assertEquals(0L, listFor(SampleRequestRole.OWNER, newOwner).getTotalHits().longValue());
  }

  @Test
  public void changeApiSampleOwner_byTheOwnersPi_closesRequestsInThePisName() {
    // in this fixture the requester is also the PI of the owner's group
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));
    transferSample(newOwner, requester);

    ApiSampleRequest closed = sampleRequestApiMgr.getRequestById(raised.getId(), owner);
    assertEquals(SampleRequestStatus.REJECTED, closed.getStatus());
    assertEquals(
        requester.getUsername(), closed.getStatusChanges().get(1).getCreatedBy().getUsername());
  }

  @Test
  public void changeApiSampleOwner_autoRejectsOtherActiveRequestsAgainstTheSample() {
    ApiSampleRequest beingFulfilled = raiseRequest("Need 2ml for the binding assay");
    ApiSampleRequest otherPending = raiseRequestAgainst(sample, "Need 1ml too");
    ApiSampleRequest otherApproved = raiseRequestAgainst(sample, "Need 3ml as well");
    sampleRequestApiMgr.updateStatus(
        otherApproved.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    // fulfil-then-transfer: a request already FULFILLED is no longer open, so the transfer
    // leaves it alone
    sampleRequestApiMgr.updateStatus(
        beingFulfilled.getId(), statusPost(SampleRequestStatus.FULFILLED, null), owner);

    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));
    transferSample(newOwner, owner);

    assertEquals(
        SampleRequestStatus.FULFILLED,
        sampleRequestApiMgr.getRequestById(beingFulfilled.getId(), owner).getStatus());

    for (ApiSampleRequest other : List.of(otherPending, otherApproved)) {
      ApiSampleRequest reloaded = sampleRequestApiMgr.getRequestById(other.getId(), owner);
      assertEquals(SampleRequestStatus.REJECTED, reloaded.getStatus());
      ApiSampleRequestStatusChange latest =
          reloaded.getStatusChanges().get(reloaded.getStatusChanges().size() - 1);
      assertEquals(SampleRequestStatus.REJECTED, latest.getStatus());
      assertTrue(
          latest.getReason().contains(newOwner.getFirstName() + " " + newOwner.getLastName()),
          "reason missing new owner's name: " + latest.getReason());
    }
  }

  @Test
  public void changeApiSampleOwner_toTheRequesterFulfilsEachOfTheirRequestsAndRejectsOthers() {
    ApiSampleRequest recipients = raiseRequest("Need 2ml for the binding assay");
    ApiSampleRequest recipientsApproved = raiseRequest("And 1ml for the control");
    sampleRequestApiMgr.updateStatus(
        recipientsApproved.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);
    User otherRequester = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    ApiSampleRequest others = raiseRequestAs(otherRequester);

    transferSample(requester, owner);

    ApiSampleRequest fulfilled = sampleRequestApiMgr.getRequestById(recipients.getId(), owner);
    assertEquals(SampleRequestStatus.FULFILLED, fulfilled.getStatus());
    ApiSampleRequestStatusChange latest = fulfilled.getStatusChanges().get(1);
    assertEquals(owner.getUsername(), latest.getCreatedBy().getUsername());
    assertEquals(sample.getGlobalId(), latest.getTransferredSample().getGlobalId());
    assertTrue(StringUtils.isNotBlank(latest.getReason()), "auto-fulfilment should say why");
    assertEquals(
        SampleRequestStatus.FULFILLED,
        sampleRequestApiMgr.getRequestById(recipientsApproved.getId(), owner).getStatus());

    assertEquals(
        SampleRequestStatus.REJECTED,
        sampleRequestApiMgr.getRequestById(others.getId(), owner).getStatus());
  }

  @Test
  public void getRequestsForUser_multipleRequesters_returnsEachRequestsOwnRequesterInfo() {
    User secondRequester =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("secondRequester"));
    ApiSampleRequest fromFirst = raiseRequestAs(requester);
    ApiSampleRequest fromSecond = raiseRequestAs(secondRequester);

    ApiSampleRequestSearchResult ownersView = listFor(SampleRequestRole.OWNER, owner);

    assertEquals(2L, ownersView.getTotalHits().longValue());
    ApiSampleRequestInfo listedFirst =
        ownersView.getRequests().stream()
            .filter(r -> r.getId().equals(fromFirst.getId()))
            .findFirst()
            .orElseThrow();
    ApiSampleRequestInfo listedSecond =
        ownersView.getRequests().stream()
            .filter(r -> r.getId().equals(fromSecond.getId()))
            .findFirst()
            .orElseThrow();

    assertEquals(requester.getUsername(), listedFirst.getRequester().getUsername());
    assertEquals(secondRequester.getUsername(), listedSecond.getRequester().getUsername());
  }

  @Test
  public void getRequestsForUser_nullRole_returnsBothSentAndReceivedRequests() {
    ApiSampleRequest sent = raiseRequest("Need 2ml for the binding assay");

    ApiSampleWithFullSubSamples requesterOwnedSample = createBasicSampleForUser(requester);
    ApiSample requesterOwnedSampleUpdate = new ApiSample();
    requesterOwnedSampleUpdate.setId(requesterOwnedSample.getId());
    requesterOwnedSampleUpdate.setRequestable(true);
    sampleApiMgr.updateApiSample(requesterOwnedSampleUpdate, requester);
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(requesterOwnedSample.getGlobalId());
    post.setNote("Need 1ml of yours");
    ApiSampleRequest received = sampleRequestApiMgr.createRequest(post, owner);

    ApiSampleRequestSearchResult results = listFor(null, requester);

    assertEquals(2L, results.getTotalHits().longValue());
    assertTrue(results.getRequests().stream().anyMatch(r -> r.getId().equals(sent.getId())));
    assertTrue(results.getRequests().stream().anyMatch(r -> r.getId().equals(received.getId())));

    // OWNER-only ("Received") must exclude the request requester themselves sent elsewhere
    ApiSampleRequestSearchResult receivedOnly = listFor(SampleRequestRole.OWNER, requester);
    assertEquals(1L, receivedOnly.getTotalHits().longValue());
    assertEquals(received.getId(), receivedOnly.getRequests().get(0).getId());

    // REQUESTER-only ("Sent") must exclude the request requester received as an owner
    ApiSampleRequestSearchResult sentOnly = listFor(SampleRequestRole.REQUESTER, requester);
    assertEquals(1L, sentOnly.getTotalHits().longValue());
    assertEquals(sent.getId(), sentOnly.getRequests().get(0).getId());
  }

  @Test
  public void getRequestsForUser_filteredBySample_returnsOnlyThatSamplesRequests() {
    ApiSampleWithFullSubSamples otherSample = createBasicSampleForUser(owner);
    markRequestable(otherSample);
    ApiSampleRequest onFirst = raiseRequest("Need 2ml for the binding assay");
    raiseRequestAgainst(otherSample, "Need 5ml of the other one");

    ApiSampleRequestSearchResult results =
        sampleRequestApiMgr.getRequestsForUser(
            PaginationCriteria.createDefaultForClass(SampleRequest.class),
            SampleRequestRole.OWNER,
            null,
            onFirst.getSample().getId(),
            owner);

    assertEquals(1L, results.getTotalHits().longValue());
    assertEquals(onFirst.getId(), results.getRequests().get(0).getId());
  }

  @Test
  public void getRequestsForUser_filteredByMultipleStatuses_returnsOnlyMatchingStatuses() {
    ApiSampleRequest pending = raiseRequest("Need 2ml for the binding assay");
    ApiSampleRequest toReject = raiseRequest("Need 5ml for another assay");
    sampleRequestApiMgr.updateStatus(
        toReject.getId(), statusPost(SampleRequestStatus.REJECTED, "no material left"), owner);

    ApiSampleRequestSearchResult activeOnly =
        sampleRequestApiMgr.getRequestsForUser(
            PaginationCriteria.createDefaultForClass(SampleRequest.class),
            SampleRequestRole.REQUESTER,
            Set.of(SampleRequestStatus.PENDING, SampleRequestStatus.APPROVED),
            null,
            requester);

    assertEquals(1L, activeOnly.getTotalHits().longValue());
    assertEquals(pending.getId(), activeOnly.getRequests().get(0).getId());

    ApiSampleRequestSearchResult pastOnly =
        sampleRequestApiMgr.getRequestsForUser(
            PaginationCriteria.createDefaultForClass(SampleRequest.class),
            SampleRequestRole.REQUESTER,
            Set.of(
                SampleRequestStatus.REJECTED,
                SampleRequestStatus.FULFILLED,
                SampleRequestStatus.CANCELLED),
            null,
            requester);

    assertEquals(1L, pastOnly.getTotalHits().longValue());
    assertEquals(toReject.getId(), pastOnly.getRequests().get(0).getId());
  }

  @Test
  public void createRequest_isAllowedForAUserWhoCannotReadTheSample() {
    // marking a sample requestable is the owner's opt-in to being asked by anyone, so discovery
    // is instance-wide and read permission is deliberately not required here
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(outsider);
    // the premise: this user genuinely cannot read the sample
    assertThrows(
        NotFoundException.class,
        () -> sampleApiMgr.assertUserCanReadSample(sample.getId(), outsider));

    ApiSampleRequest raised = raiseRequestAs(outsider);

    assertEquals(SampleRequestStatus.PENDING, raised.getStatus());
    assertEquals(outsider.getUsername(), raised.getRequester().getUsername());
  }

  @Test
  public void transferredSample_isShownOnlyAsFarAsTheViewerMayRead() {
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(outsider);
    ApiSampleRequest raised = raiseRequestAs(outsider);
    ApiSampleWithFullSubSamples aliquot = createBasicSampleForUser(owner);
    ApiSample described = new ApiSample();
    described.setId(aliquot.getId());
    described.setDescription("freezer 3, box 12");
    sampleApiMgr.updateApiSample(described, owner);
    ApiSampleRequestStatusPut fulfil = statusPost(SampleRequestStatus.FULFILLED, null);
    fulfil.setTransferredSampleGlobalId(aliquot.getGlobalId());
    sampleRequestApiMgr.updateStatus(raised.getId(), fulfil, owner);

    // the outsider cannot read the aliquot, so gets only its public view
    ApiSampleInfo seenByOutsider =
        sampleRequestApiMgr
            .getRequestById(raised.getId(), outsider)
            .getStatusChanges()
            .get(1)
            .getTransferredSample();
    assertEquals(aliquot.getGlobalId(), seenByOutsider.getGlobalId());
    assertNull(seenByOutsider.getDescription());

    ApiSampleInfo seenByOwner =
        sampleRequestApiMgr
            .getRequestById(raised.getId(), owner)
            .getStatusChanges()
            .get(1)
            .getTransferredSample();
    assertEquals("freezer 3, box 12", seenByOwner.getDescription());
  }

  @Test
  public void updateStatus_refusesATransferredSampleTheFulfillerCannotRead() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User stranger = createAndSaveUserIfNotExists(getRandomAlphabeticString("stranger"));
    initialiseContentWithEmptyContent(stranger);
    ApiSampleWithFullSubSamples strangersSample = createBasicSampleForUser(stranger);
    // the premise: the fulfiller genuinely cannot read it
    assertThrows(
        NotFoundException.class,
        () -> sampleApiMgr.assertUserCanReadSample(strangersSample.getId(), owner));

    ApiSampleRequestStatusPut fulfil = statusPost(SampleRequestStatus.FULFILLED, null);
    fulfil.setTransferredSampleGlobalId(strangersSample.getGlobalId());

    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.updateStatus(raised.getId(), fulfil, owner));
    assertEquals(
        SampleRequestStatus.PENDING,
        sampleRequestApiMgr.getRequestById(raised.getId(), owner).getStatus());
  }

  @Test
  public void updateStatus_refusesATransferredSampleTheFulfillerCanOnlyLimitedRead() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User stranger = createAndSaveUserIfNotExists(getRandomAlphabeticString("stranger"));
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(stranger, pi);
    Group ownersGroup = createGroup("ownersGroup", pi);
    addUsersToGroup(pi, ownersGroup, owner);
    // a stranger's subsample in a container shared with the owner's group gives limited read only
    ApiContainer shared = createBasicContainerForUser(stranger, "shared", List.of(ownersGroup));
    ApiSampleWithFullSubSamples strangersSample = createBasicSampleForUser(stranger);
    moveSubSampleIntoListContainer(
        strangersSample.getSubSamples().get(0).getId(), shared.getId(), stranger);
    assertEquals(
        List.of(ApiInventoryRecordPermittedAction.LIMITED_READ),
        sampleApiMgr.getApiSampleById(strangersSample.getId(), owner).getPermittedActions());

    ApiSampleRequestStatusPut fulfil = statusPost(SampleRequestStatus.FULFILLED, null);
    fulfil.setTransferredSampleGlobalId(strangersSample.getGlobalId());

    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.updateStatus(raised.getId(), fulfil, owner));
  }

  @Test
  public void updateStatus_refusesATrashedTransferredSample() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    ApiSampleWithFullSubSamples trashed = createBasicSampleForUser(owner);
    sampleApiMgr.markSampleAsDeleted(trashed.getId(), false, owner);

    ApiSampleRequestStatusPut fulfil = statusPost(SampleRequestStatus.FULFILLED, null);
    fulfil.setTransferredSampleGlobalId(trashed.getGlobalId());

    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.updateStatus(raised.getId(), fulfil, owner));
  }

  @Test
  public void createRequest_isRefusedWhenSampleRequestsAreOffForTheSampleOwner() {
    User otherPi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User otherOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("otherOwner"));
    initialiseContentWithEmptyContent(otherPi, otherOwner);
    Group otherGroup = createGroup("otherGroup", otherPi);
    addUsersToGroup(otherPi, otherGroup, otherOwner);
    ApiSampleWithFullSubSamples theirSample = createBasicSampleForUser(otherOwner);
    ApiSample requestable = new ApiSample();
    requestable.setId(theirSample.getId());
    requestable.setRequestable(true);
    sampleApiMgr.updateApiSample(requestable, otherOwner);
    // the owner's community then switches sample requests off; the requester's has them on
    User sysadmin = logoutAndLoginAsSysAdmin();
    Community community = createAndSaveCommunity(sysadmin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(otherGroup.getId(), community.getId(), sysadmin);
    SystemPropertyValue off =
        new SystemPropertyValue(
            systemPropertyMgr
                .findByName(SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE)
                .getProperty(),
            HierarchicalPermission.DENIED.toString(),
            community);
    systemPropertyMgr.save(off, sysadmin);

    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(theirSample.getGlobalId());
    post.setNote("Need 2ml for the binding assay");
    assertRefusedWith(
        "errors.inventory.sampleRequest.notRequestable",
        () -> sampleRequestApiMgr.createRequest(post, requester));
  }

  @Test
  public void requestParties_seeOnlyEachOthersPublicDetails() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    ApiSampleRequest seenByRequester =
        sampleRequestApiMgr.getRequestById(raised.getId(), requester);
    ApiSampleRequestInfo listedForRequester =
        listFor(SampleRequestRole.REQUESTER, requester).getRequests().get(0);
    for (ApiUser party :
        List.of(
            seenByRequester.getRequester(),
            seenByRequester.getOriginalOwner(),
            seenByRequester.getStatusChanges().get(1).getCreatedBy(),
            listedForRequester.getOriginalOwner())) {
      assertNotNull(party.getUsername());
      assertNull(party.getEmail(), party.getUsername() + "'s email");
      assertNull(party.getHomeFolderId(), party.getUsername() + "'s home folder");
      assertNull(party.getHasPiRole(), party.getUsername() + "'s roles");
    }
  }

  @Test
  public void createRequest_isRefusedWhenSampleIsNotRequestable() {
    ApiSampleWithFullSubSamples notRequestable = createBasicSampleForUser(owner);

    assertRefusedWith(
        "errors.inventory.sampleRequest.notRequestable",
        () -> raiseRequestAgainst(notRequestable, "not published for requests"));
  }

  @Test
  public void createRequest_isRefusedWhenRequesterOwnsTheSample() {
    assertRefusedWith("errors.inventory.sampleRequest.ownSample", () -> raiseRequestAs(owner));
  }

  @Test
  public void getRequestById_isVisibleToRequesterAndSampleOwnerOnly() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(outsider);

    assertEquals(
        raised.getId(), sampleRequestApiMgr.getRequestById(raised.getId(), requester).getId());
    assertEquals(raised.getId(), sampleRequestApiMgr.getRequestById(raised.getId(), owner).getId());

    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.getRequestById(raised.getId(), outsider));
  }

  @Test
  public void getRequestById_remainsVisibleToTheOriginalOwnerAfterATransfer() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));

    transferSample(newOwner, owner);

    // the original owner keeps seeing this in their "Received" listing (originalOwner is fixed
    // at creation), so they must still be able to open it
    assertEquals(raised.getId(), sampleRequestApiMgr.getRequestById(raised.getId(), owner).getId());

    // the sample's new owner can open it too, though the transfer has rejected it
    assertEquals(
        raised.getId(), sampleRequestApiMgr.getRequestById(raised.getId(), newOwner).getId());
  }

  @Test
  public void getRequestsForUser_excludesRequestsAgainstDeletedSamples() {
    raiseRequest("Need 2ml for the binding assay");
    sampleApiMgr.markSampleAsDeleted(sample.getId(), true, owner);

    assertEquals(0L, listFor(SampleRequestRole.OWNER, owner).getTotalHits().longValue());
    assertEquals(0L, listFor(SampleRequestRole.REQUESTER, requester).getTotalHits().longValue());
  }

  @Test
  public void requestAgainstADeletedSample_staysReadableAndActionableById() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    sampleApiMgr.markSampleAsDeleted(sample.getId(), true, owner);

    assertEquals(
        raised.getId(), sampleRequestApiMgr.getRequestById(raised.getId(), requester).getId());
    ApiSampleRequest rejected =
        sampleRequestApiMgr.updateStatus(
            raised.getId(), statusPost(SampleRequestStatus.REJECTED, "sample discarded"), owner);
    assertEquals(SampleRequestStatus.REJECTED, rejected.getStatus());
  }

  @Test
  public void createRequest_isRefusedForADeletedSample() {
    sampleApiMgr.markSampleAsDeleted(sample.getId(), true, owner);

    // still flagged requestable, but a trashed sample is gone as far as requests are concerned
    assertThrows(NotFoundException.class, () -> raiseRequest("Need 2ml for the binding assay"));
  }

  @Test
  public void everyOperationIsRefusedWhenSampleRequestsDisabled() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    assertThrows(UnsupportedOperationException.class, () -> raiseRequestAs(requester));
    assertThrows(
        UnsupportedOperationException.class, () -> listFor(SampleRequestRole.REQUESTER, requester));
    assertThrows(
        UnsupportedOperationException.class,
        () -> sampleRequestApiMgr.getRequestById(raised.getId(), requester));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner));
  }

  @Test
  public void getRequestById_reportsCreationAsAPendingStatusChange() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest fetched = sampleRequestApiMgr.getRequestById(raised.getId(), requester);

    assertEquals(1, fetched.getStatusChanges().size());
    ApiSampleRequestStatusChange creation = fetched.getStatusChanges().get(0);
    assertNotNull(creation.getId(), "the creation entry must be a persisted row, not synthesised");
    assertEquals(SampleRequestStatus.PENDING, creation.getStatus());
    assertEquals(requester.getUsername(), creation.getCreatedBy().getUsername());
    assertEquals(fetched.getCreatedMillis(), creation.getCreatedMillis());
  }

  @Test
  public void createRequest_isRefusedForANonSampleGlobalId() {
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    // a container global id whose db id happens to match a real sample must not be accepted
    post.setSampleGlobalId("IC" + sample.getId());
    post.setNote("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.globalId.unsupportedType",
        () -> sampleRequestApiMgr.createRequest(post, requester));
  }

  @Test
  public void createRequest_isRefusedForAMalformedGlobalId() {
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId("not a global id");
    post.setNote("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.globalId.unsupportedType",
        () -> sampleRequestApiMgr.createRequest(post, requester));
  }

  @Test
  public void updateStatus_ownerApprovesPendingRequest() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest approved =
        sampleRequestApiMgr.updateStatus(
            raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    assertEquals(SampleRequestStatus.APPROVED, approved.getStatus());
    assertEquals(2, approved.getStatusChanges().size());
    ApiSampleRequestStatusChange latest = approved.getStatusChanges().get(1);
    assertEquals(SampleRequestStatus.APPROVED, latest.getStatus());
    assertEquals(owner.getUsername(), latest.getCreatedBy().getUsername());
    assertNull(latest.getReason());
  }

  @Test
  public void updateStatus_ownerRejectsWithAReasonStoredOnTheRow() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest rejected =
        sampleRequestApiMgr.updateStatus(
            raised.getId(),
            statusPost(SampleRequestStatus.REJECTED, "Insufficient material left"),
            owner);

    assertEquals(SampleRequestStatus.REJECTED, rejected.getStatus());
    ApiSampleRequestStatusChange latest = rejected.getStatusChanges().get(1);
    assertEquals("Insufficient material left", latest.getReason());
    assertEquals(owner.getUsername(), latest.getCreatedBy().getUsername());
  }

  @Test
  public void updateStatus_ownerRejectsAnApprovedRequestWithAReason() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    ApiSampleRequest rejected =
        sampleRequestApiMgr.updateStatus(
            raised.getId(),
            statusPost(SampleRequestStatus.REJECTED, "The aliquot was contaminated"),
            owner);

    assertEquals(SampleRequestStatus.REJECTED, rejected.getStatus());
    assertEquals(3, rejected.getStatusChanges().size());
    assertEquals("The aliquot was contaminated", rejected.getStatusChanges().get(2).getReason());
  }

  @Test
  public void updateStatus_rejectionWithoutAReasonIsRefused() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.sampleRequest.reasonRequired",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.REJECTED, "  "), owner));
  }

  @Test
  public void updateStatus_reasonOnANonRejectionIsRefused() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.sampleRequest.reasonNotAllowed",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(),
                statusPost(SampleRequestStatus.APPROVED, "no reason expected here"),
                owner));
  }

  @Test
  public void updateStatus_requesterCancelsTheirOwnPendingRequest() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest cancelled =
        sampleRequestApiMgr.updateStatus(
            raised.getId(), statusPost(SampleRequestStatus.CANCELLED, null), requester);

    assertEquals(SampleRequestStatus.CANCELLED, cancelled.getStatus());
    assertEquals(
        requester.getUsername(), cancelled.getStatusChanges().get(1).getCreatedBy().getUsername());
  }

  @Test
  public void updateStatus_byANonParty_isNotFoundSoTheRequestStaysUndisclosed() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(outsider);

    assertThrows(
        NotFoundException.class,
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), outsider));
  }

  @Test
  public void updateStatus_requesterCannotCancelAnApprovedRequest() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    assertRefusedWith(
        "errors.inventory.sampleRequest.illegalTransition",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.CANCELLED, null), requester));
  }

  @Test
  public void updateStatus_isRefusedForTheWrongActor() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    // the owner cannot withdraw it on the requester's behalf
    assertRefusedWith(
        "errors.inventory.sampleRequest.wrongActor",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.CANCELLED, null), owner));
    // nor can the requester fulfil their own request
    assertRefusedWith(
        "errors.inventory.sampleRequest.wrongActor",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.FULFILLED, null), requester));
  }

  @Test
  public void updateStatus_checksTheActorBeforeWhetherTheTransitionIsLegal() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    // approving again is both illegal from APPROVED and not the requester's to do
    assertRefusedWith(
        "errors.inventory.sampleRequest.wrongActor",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), requester));
  }

  @Test
  public void updateStatus_isRefusedForAnIllegalTransition() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner);

    // approving twice is not legal, the request is no longer PENDING
    assertRefusedWith(
        "errors.inventory.sampleRequest.illegalTransition",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.APPROVED, null), owner));

    // APPROVED to FULFILLED is legal, and FULFILLED is terminal
    sampleRequestApiMgr.updateStatus(
        raised.getId(), statusPost(SampleRequestStatus.FULFILLED, null), owner);
    assertRefusedWith(
        "errors.inventory.sampleRequest.illegalTransition",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.REJECTED, "too late"), owner));
  }

  @Test
  public void updateStatus_ownerFulfilsPendingRequestDirectly() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    // FULFILLED is reachable directly from PENDING, skipping a separate approval step
    ApiSampleRequest fulfilled =
        sampleRequestApiMgr.updateStatus(
            raised.getId(), statusPost(SampleRequestStatus.FULFILLED, null), owner);

    assertEquals(SampleRequestStatus.FULFILLED, fulfilled.getStatus());
    ApiSampleRequestStatusChange latest = fulfilled.getStatusChanges().get(1);
    assertEquals(SampleRequestStatus.FULFILLED, latest.getStatus());
    assertEquals(owner.getUsername(), latest.getCreatedBy().getUsername());
    assertNull(latest.getReason());
    assertNull(latest.getTransferredSample());
  }

  @Test
  public void updateStatus_cannotSetPendingExplicitly() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.sampleRequest.statusNotSettable",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.PENDING, null), owner));
  }

  @Test
  public void updateStatus_blankTransferredSampleIsTreatedAsAbsent() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest fulfilled =
        sampleRequestApiMgr.updateStatus(
            raised.getId(), statusPost(SampleRequestStatus.FULFILLED, null, ""), owner);

    ApiSampleRequestStatusChange latest =
        fulfilled.getStatusChanges().get(fulfilled.getStatusChanges().size() - 1);
    assertEquals(SampleRequestStatus.FULFILLED, latest.getStatus());
    assertNull(latest.getTransferredSample());
  }

  @Test
  public void updateStatus_unknownTransferredSampleIsRefused() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    assertThrows(
        NotFoundException.class,
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(),
                statusPost(SampleRequestStatus.FULFILLED, null, "SA" + Long.MAX_VALUE),
                owner));
  }

  @Test
  public void updateStatus_transferredSampleIsOnlyAcceptedWhenFulfilling() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    assertRefusedWith(
        "errors.inventory.sampleRequest.transferredSampleNotAllowed",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(),
                statusPost(SampleRequestStatus.APPROVED, null, sample.getGlobalId()),
                owner));
    assertEquals(
        SampleRequestStatus.PENDING,
        sampleRequestApiMgr.getRequestById(raised.getId(), owner).getStatus());
  }

  @Test
  public void updateStatus_transferredSampleMustNameASample() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    String subSampleGlobalId = sample.getSubSamples().get(0).getGlobalId();

    assertRefusedWith(
        "errors.inventory.globalId.unsupportedType",
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(),
                statusPost(SampleRequestStatus.FULFILLED, null, subSampleGlobalId),
                owner));
  }

  @Test
  public void accountReusingTheRequestersUsername_cannotSeeListOrCancelTheRequest() {
    User original = requester;
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User reused = takeOverUsername(original);

    assertThrows(
        NotFoundException.class, () -> sampleRequestApiMgr.getRequestById(raised.getId(), reused));
    assertEquals(0L, listFor(SampleRequestRole.REQUESTER, reused).getTotalHits().longValue());
    assertThrows(
        NotFoundException.class,
        () ->
            sampleRequestApiMgr.updateStatus(
                raised.getId(), statusPost(SampleRequestStatus.CANCELLED, null), reused));

    // the owner still sees the original requester, not the account now holding the username
    ApiSampleRequest seenByOwner = sampleRequestApiMgr.getRequestById(raised.getId(), owner);
    assertEquals(original.getId(), seenByOwner.getRequester().getId());
    assertEquals(original.getId(), seenByOwner.getStatusChanges().get(0).getCreatedBy().getId());
  }

  @Test
  public void accountReusingTheOriginalOwnersUsername_cannotSeeTheRequestAfterTransfer() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));
    transferSample(newOwner, owner);
    User reused = takeOverUsername(owner);

    assertThrows(
        NotFoundException.class, () -> sampleRequestApiMgr.getRequestById(raised.getId(), reused));
    assertEquals(0L, listFor(SampleRequestRole.OWNER, reused).getTotalHits().longValue());
  }

  @Test
  public void transferToAnAccountReusingTheRequestersUsername_rejectsTheirRequest() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    User reused = takeOverUsername(requester);

    transferSample(reused, owner);

    assertEquals(
        SampleRequestStatus.REJECTED,
        sampleRequestApiMgr.getRequestById(raised.getId(), owner).getStatus());
  }

  /**
   * Moves the user to a new username and gives their old one to a new account, as when a deleted
   * user's username is registered again.
   */
  private User takeOverUsername(User previousHolder) {
    String username = previousHolder.getUsername();
    previousHolder.setUsername(getRandomAlphabeticString("moved"));
    userMgr.save(previousHolder);
    User reused = createAndSaveUserIfNotExists(username);
    initialiseContentWithEmptyContent(reused);
    return reused;
  }

  private ApiSampleRequest raiseRequestAs(User user) {
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("Need 2ml for the binding assay");
    return sampleRequestApiMgr.createRequest(post, user);
  }

  private ApiSampleRequest raiseRequest(String note) {
    return raiseRequestAgainst(sample, note);
  }

  private ApiSampleRequest raiseRequestAgainst(ApiSampleWithFullSubSamples target, String note) {
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(target.getGlobalId());
    post.setNote(note);
    return sampleRequestApiMgr.createRequest(post, requester);
  }

  private ApiSampleRequestSearchResult listFor(SampleRequestRole role, User user) {
    return sampleRequestApiMgr.getRequestsForUser(
        PaginationCriteria.createDefaultForClass(SampleRequest.class), role, null, null, user);
  }

  private ApiSampleRequestStatusPut statusPost(SampleRequestStatus status, String reason) {
    return statusPost(status, reason, null);
  }

  private ApiSampleRequestStatusPut statusPost(
      SampleRequestStatus status, String reason, String transferredSampleGlobalId) {
    ApiSampleRequestStatusPut post = new ApiSampleRequestStatusPut();
    post.setStatus(status);
    post.setReason(reason);
    post.setTransferredSampleGlobalId(transferredSampleGlobalId);
    return post;
  }

  private void markRequestable(ApiSampleWithFullSubSamples target) {
    ApiSample update = new ApiSample();
    update.setId(target.getId());
    update.setRequestable(true);
    sampleApiMgr.updateApiSample(update, owner);
  }

  private void transferSample(User newOwner, User transferredBy) {
    ApiSample transfer = new ApiSample();
    transfer.setId(sample.getId());
    transfer.setOwner(new ApiUser(newOwner));
    sampleApiMgr.changeApiSampleOwner(transfer, transferredBy);
  }

  private static void assertRefusedWith(String messageKey, Executable call) {
    assertEquals(messageKey, assertThrows(ApiRuntimeException.class, call).getMessage());
  }
}
