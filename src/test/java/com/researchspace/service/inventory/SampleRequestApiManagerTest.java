package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleRequestStatusChange;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.SampleRequestDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.ws.rs.NotFoundException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SampleRequestApiManagerTest extends SpringTransactionalTest {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;
  private @Autowired SampleRequestDao sampleRequestDao;

  private User owner;
  private User requester;
  private ApiSampleWithFullSubSamples sample;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    requester = createAndSaveAPi();
    owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    initialiseContentWithEmptyContent(requester, owner);
    createGroupForUsers(requester, requester.getUsername(), "", requester, owner);

    sample = createBasicSampleForUser(owner);
    markRequestable(sample);
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
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
  }

  @Test
  public void getRequestsForUser_asRequester_returnsOwnRequests() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequestSearchResult results = listFor(SampleRequestRole.REQUESTER, requester);

    assertEquals(1L, results.getTotalHits().longValue());
    assertEquals(raised.getId(), results.getRequests().get(0).getId());
  }

  @Test
  public void getRequestsForUser_asSampleOwner_returnsRequestsAgainstOwnedSamples() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequestSearchResult ownersView = listFor(SampleRequestRole.OWNER, owner);
    assertEquals(1L, ownersView.getTotalHits().longValue());
    assertEquals(raised.getId(), ownersView.getRequests().get(0).getId());

    // the requester owns no requested sample, so nothing is awaiting them
    assertEquals(0L, listFor(SampleRequestRole.OWNER, requester).getTotalHits().longValue());
  }

  @Test
  public void getRequestsForUser_filteredBySample_returnsOnlyThatSamplesRequests() {
    ApiSampleWithFullSubSamples otherSample = createBasicSampleForUser(owner);
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
    SampleRequest rejectedEntity = sampleRequestDao.get(toReject.getId());
    rejectedEntity.setStatus(SampleRequestStatus.REJECTED);
    sampleRequestDao.save(rejectedEntity);

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

    ApiSampleRequest raised = raiseRequestAs(outsider);

    assertEquals(SampleRequestStatus.PENDING, raised.getStatus());
    assertEquals(outsider.getUsername(), raised.getRequester().getUsername());
  }

  @Test
  public void createRequest_isRefusedWhenSampleIsNotRequestable() {
    ApiSampleWithFullSubSamples notRequestable = createBasicSampleForUser(owner);

    assertThrows(
        ApiRuntimeException.class,
        () -> raiseRequestAgainst(notRequestable, "not published for requests"));
  }

  @Test
  public void createRequest_isRefusedWhenRequesterOwnsTheSample() {
    assertThrows(ApiRuntimeException.class, () -> raiseRequestAs(owner));
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
  public void getRequestsForUser_excludesRequestsAgainstDeletedSamples() {
    raiseRequest("Need 2ml for the binding assay");
    sampleApiMgr.markSampleAsDeleted(sample.getId(), true, owner);

    assertEquals(0L, listFor(SampleRequestRole.OWNER, owner).getTotalHits().longValue());
    assertEquals(0L, listFor(SampleRequestRole.REQUESTER, requester).getTotalHits().longValue());
  }

  @Test
  public void createRequest_isRefusedWhenSampleRequestsDisabled() {
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    assertThrows(UnsupportedOperationException.class, () -> raiseRequestAs(requester));
  }

  @Test
  public void getRequestById_reportsCreationAsAPendingStatusChange() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");

    ApiSampleRequest fetched = sampleRequestApiMgr.getRequestById(raised.getId(), requester);

    assertEquals(1, fetched.getStatusChanges().size());
    ApiSampleRequestStatusChange creation = fetched.getStatusChanges().get(0);
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

    assertThrows(
        ApiRuntimeException.class, () -> sampleRequestApiMgr.createRequest(post, requester));
  }

  @Test
  public void getRequestsForUser_isRefusedWhenSampleRequestsDisabled() {
    raiseRequest("Need 2ml for the binding assay");
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    assertThrows(
        UnsupportedOperationException.class, () -> listFor(SampleRequestRole.REQUESTER, requester));
  }

  @Test
  public void getRequestById_isRefusedWhenSampleRequestsDisabled() {
    ApiSampleRequest raised = raiseRequest("Need 2ml for the binding assay");
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    assertThrows(
        UnsupportedOperationException.class,
        () -> sampleRequestApiMgr.getRequestById(raised.getId(), requester));
  }

  private void markRequestable(ApiSampleWithFullSubSamples target) {
    ApiSample update = new ApiSample();
    update.setId(target.getId());
    update.setRequestable(true);
    sampleApiMgr.updateApiSample(update, owner);
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
}
