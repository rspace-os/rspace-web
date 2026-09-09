package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
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

    this.mockMvc
        .perform(
            createBuilderForInventoryPostWithJSONBody(
                ownerApiKey,
                "/sampleRequests",
                owner,
                Map.of("sampleGlobalId", sample.getGlobalId(), "note", "requesting my own")))
        .andExpect(status().isUnprocessableEntity());
  }
}
