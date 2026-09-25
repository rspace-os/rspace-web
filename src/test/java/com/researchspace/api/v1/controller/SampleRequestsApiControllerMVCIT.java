package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyName;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class SampleRequestsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createThenListSampleRequest() throws Exception {
    User owner = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User requester = createAndSaveUser(getRandomName(10));
    initUsers(owner, requester);
    createGroupForUsersWithDefaultPi(owner, requester);

    String requesterApiKey = createNewApiKeyForUser(requester);
    String ownerApiKey = createNewApiKeyForUser(owner);
    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    markRequestable(sample, owner);

    MvcResult postResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPostWithJSONBody(
                    requesterApiKey,
                    "/sampleRequests",
                    requester,
                    Map.of(
                        "sampleGlobalId",
                        sample.getGlobalId(),
                        "note",
                        "Need 2ml for the binding assay")))
            .andReturn();
    assertNull(postResult.getResolvedException());
    ApiSampleRequest created = mvcUtils.getFromJsonResponseBody(postResult, ApiSampleRequest.class);
    assertEquals(SampleRequestStatus.PENDING, created.getStatus());
    assertEquals(sample.getGlobalId(), created.getSample().getGlobalId());

    // the owner sees it in their queue, which exercises role param binding
    MvcResult getResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryGet(API_VERSION.ONE, ownerApiKey, "/sampleRequests", owner)
                    .param("role", "OWNER"))
            .andReturn();
    assertNull(getResult.getResolvedException());
    ApiSampleRequestSearchResult listed =
        mvcUtils.getFromJsonResponseBody(getResult, ApiSampleRequestSearchResult.class);
    assertEquals(1L, listed.getTotalHits().longValue());
    assertEquals(created.getId(), listed.getRequests().get(0).getId());
  }

  private void markRequestable(ApiSampleWithFullSubSamples target, User owner) {
    ApiSample update = new ApiSample();
    update.setId(target.getId());
    update.setRequestable(true);
    sampleApiMgr.updateApiSample(update, owner);
  }

  @Test
  public void createRequest_forOwnSampleIsUnprocessable() throws Exception {
    User owner = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    initUsers(owner);
    String ownerApiKey = createNewApiKeyForUser(owner);
    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    markRequestable(sample, owner);

    this.mockMvc
        .perform(
            createBuilderForInventoryPostWithJSONBody(
                ownerApiKey,
                "/sampleRequests",
                owner,
                Map.of("sampleGlobalId", sample.getGlobalId(), "note", "requesting my own")))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  public void allEndpointsAreNotFoundWhenSampleRequestsDisabled() throws Exception {
    User owner = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User requester = createAndSaveUser(getRandomName(10));
    initUsers(owner, requester);
    createGroupForUsersWithDefaultPi(owner, requester);

    String requesterApiKey = createNewApiKeyForUser(requester);
    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    markRequestable(sample, owner);

    // raise one while the feature is on, so there is something for GET /{id} to find
    MvcResult postResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPostWithJSONBody(
                    requesterApiKey,
                    "/sampleRequests",
                    requester,
                    Map.of("sampleGlobalId", sample.getGlobalId(), "note", "before switch-off")))
            .andReturn();
    ApiSampleRequest created = mvcUtils.getFromJsonResponseBody(postResult, ApiSampleRequest.class);

    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    // the whole resource behaves as if it is not there, rather than erroring
    this.mockMvc
        .perform(
            createBuilderForInventoryPostWithJSONBody(
                requesterApiKey,
                "/sampleRequests",
                requester,
                Map.of("sampleGlobalId", sample.getGlobalId(), "note", "after switch-off")))
        .andExpect(status().isNotFound());

    this.mockMvc
        .perform(
            createBuilderForInventoryGet(
                API_VERSION.ONE, requesterApiKey, "/sampleRequests", requester))
        .andExpect(status().isNotFound());

    this.mockMvc
        .perform(
            createBuilderForInventoryGet(
                API_VERSION.ONE, requesterApiKey, "/sampleRequests/" + created.getId(), requester))
        .andExpect(status().isNotFound());

    this.mockMvc
        .perform(
            createBuilderForInventoryPutWithJSONBody(
                requesterApiKey,
                "/sampleRequests/" + created.getId() + "/status",
                requester,
                Map.of("status", "CANCELLED")))
        .andExpect(status().isNotFound());
  }

  @Test
  public void approveOverHttpThenRefuseAnIllegalTransition() throws Exception {
    User owner = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User requester = createAndSaveUser(getRandomName(10));
    initUsers(owner, requester);
    createGroupForUsersWithDefaultPi(owner, requester);

    String requesterApiKey = createNewApiKeyForUser(requester);
    String ownerApiKey = createNewApiKeyForUser(owner);
    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    markRequestable(sample, owner);

    MvcResult postResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPostWithJSONBody(
                    requesterApiKey,
                    "/sampleRequests",
                    requester,
                    Map.of("sampleGlobalId", sample.getGlobalId(), "note", "for approval")))
            .andReturn();
    ApiSampleRequest created = mvcUtils.getFromJsonResponseBody(postResult, ApiSampleRequest.class);

    MvcResult approveResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPutWithJSONBody(
                    ownerApiKey,
                    "/sampleRequests/" + created.getId() + "/status",
                    owner,
                    Map.of("status", "APPROVED")))
            .andReturn();
    assertNull(approveResult.getResolvedException());
    ApiSampleRequest approved =
        mvcUtils.getFromJsonResponseBody(approveResult, ApiSampleRequest.class);
    assertEquals(SampleRequestStatus.APPROVED, approved.getStatus());
    assertEquals(2, approved.getStatusChanges().size());

    // approving again is no longer a legal transition
    this.mockMvc
        .perform(
            createBuilderForInventoryPutWithJSONBody(
                ownerApiKey,
                "/sampleRequests/" + created.getId() + "/status",
                owner,
                Map.of("status", "APPROVED")))
        .andExpect(status().isUnprocessableEntity());

    // and the requester may not approve at all
    this.mockMvc
        .perform(
            createBuilderForInventoryPutWithJSONBody(
                requesterApiKey,
                "/sampleRequests/" + created.getId() + "/status",
                requester,
                Map.of("status", "APPROVED")))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  public void fullRequestLifecycleOverHttp() throws Exception {
    User owner = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User requester = createAndSaveUser(getRandomName(10));
    initUsers(owner, requester);
    createGroupForUsersWithDefaultPi(owner, requester);

    String requesterApiKey = createNewApiKeyForUser(requester);
    String ownerApiKey = createNewApiKeyForUser(owner);
    sysPropMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    markRequestable(sample, owner);

    // raise it
    MvcResult postResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPostWithJSONBody(
                    requesterApiKey,
                    "/sampleRequests",
                    requester,
                    Map.of("sampleGlobalId", sample.getGlobalId(), "note", "2ml for the assay")))
            .andReturn();
    assertNull(postResult.getResolvedException());
    ApiSampleRequest created = mvcUtils.getFromJsonResponseBody(postResult, ApiSampleRequest.class);
    assertEquals(SampleRequestStatus.PENDING, created.getStatus());

    // the requester sees it among their own
    assertEquals(
        1L, listRequests(requesterApiKey, requester, "REQUESTER", null).getTotalHits().longValue());

    // and it is waiting in the owner's pending queue, which drives the badge count
    assertEquals(
        1L, listRequests(ownerApiKey, owner, "OWNER", "PENDING").getTotalHits().longValue());

    // the owner approves
    MvcResult approveResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryPutWithJSONBody(
                    ownerApiKey,
                    "/sampleRequests/" + created.getId() + "/status",
                    owner,
                    Map.of("status", "APPROVED")))
            .andReturn();
    assertNull(approveResult.getResolvedException());

    // fetching the single request returns the full history, actors resolved
    MvcResult detailResult =
        this.mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE,
                    requesterApiKey,
                    "/sampleRequests/" + created.getId(),
                    requester))
            .andReturn();
    assertNull(detailResult.getResolvedException());
    ApiSampleRequest detail =
        mvcUtils.getFromJsonResponseBody(detailResult, ApiSampleRequest.class);
    assertEquals(SampleRequestStatus.APPROVED, detail.getStatus());
    assertEquals(requester.getUsername(), detail.getRequester().getUsername());
    assertEquals(2, detail.getStatusChanges().size());
    assertEquals(SampleRequestStatus.PENDING, detail.getStatusChanges().get(0).getStatus());
    assertEquals(
        requester.getUsername(), detail.getStatusChanges().get(0).getCreatedBy().getUsername());
    assertEquals(SampleRequestStatus.APPROVED, detail.getStatusChanges().get(1).getStatus());
    assertEquals(
        owner.getUsername(), detail.getStatusChanges().get(1).getCreatedBy().getUsername());

    // the pending queue has emptied, and the multi-status filter still finds it
    assertEquals(
        0L, listRequests(ownerApiKey, owner, "OWNER", "PENDING").getTotalHits().longValue());
    assertEquals(
        1L,
        listRequests(ownerApiKey, owner, "OWNER", "PENDING,APPROVED").getTotalHits().longValue());
  }

  private ApiSampleRequestSearchResult listRequests(
      String apiKey, User user, String role, String statuses) throws Exception {
    MockHttpServletRequestBuilder request =
        createBuilderForInventoryGet(API_VERSION.ONE, apiKey, "/sampleRequests", user)
            .param("role", role);
    if (statuses != null) {
      request = request.param("status", statuses);
    }
    MvcResult result = this.mockMvc.perform(request).andReturn();
    assertNull(result.getResolvedException());
    return mvcUtils.getFromJsonResponseBody(result, ApiSampleRequestSearchResult.class);
  }
}
