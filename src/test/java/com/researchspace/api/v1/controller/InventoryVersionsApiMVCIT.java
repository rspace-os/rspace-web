package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage of the user-version endpoints added for RSDEV-1141: GET
 * /{samples|subSamples|containers}/{id}/versions/{version} and the container revisions endpoints,
 * exercising real Envers auditing.
 */
public class InventoryVersionsApiMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void sampleVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples createdSample =
        createBasicSampleForUser(anyUser, "version one name");

    // edit the sample, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/samples/" + createdSample.getId(),
                anyUser,
                "{ \"name\": \"version two name\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // version 1 resolves to the historical snapshot
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + createdSample.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleV1 = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("version one name", sampleV1.getName());
    assertTrue(sampleV1.isHistoricalVersion());
    assertEquals(Long.valueOf(1L), sampleV1.getVersion());
    assertNotNull(sampleV1.getRevisionId());
    assertEquals("SA" + createdSample.getId() + "v1", sampleV1.getGlobalId());

    // the current version resolves to the live record, not flagged historical
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + createdSample.getId() + "/versions/2",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleV2 = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("version two name", sampleV2.getName());
    assertEquals(false, sampleV2.isHistoricalVersion());

    // a version that never existed is a clean 404
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/samples/" + createdSample.getId() + "/versions/99",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void subSampleVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(anyUser);
    Long subSampleId = createdSample.getSubSamples().get(0).getId();

    // edit the subsample, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/subSamples/" + subSampleId,
                anyUser,
                "{ \"name\": \"subsample renamed\" }"))
        .andExpect(status().isOk())
        .andReturn();

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/versions/1", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleV1 = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("mySubSample", subSampleV1.getName());
    assertTrue(subSampleV1.isHistoricalVersion());
    assertEquals(Long.valueOf(1L), subSampleV1.getVersion());
    assertEquals("SS" + subSampleId + "v1", subSampleV1.getGlobalId());

    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/versions/99", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void containerRevisionsAndVersionRetrieval() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer container = createBasicContainerForUser(anyUser, "version one container");

    // edit the container, bumping it to version 2
    this.mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/containers/" + container.getId(),
                anyUser,
                "{ \"name\": \"version two container\" }"))
        .andExpect(status().isOk())
        .andReturn();

    // revisions list is now available for containers
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    // single revision retrieval, without content
    Long firstRevisionId = history.getRevisions().get(0).getRevisionId();
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/revisions/" + firstRevisionId,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer containerRev = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version one container", containerRev.getName());
    // locations are not audited, so historical containers are returned without content
    assertNull(containerRev.getLocations());

    // version lookup resolves the historical snapshot
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/containers/" + container.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiContainer containerV1 = getFromJsonResponseBody(result, ApiContainer.class);
    assertEquals("version one container", containerV1.getName());
    assertTrue(containerV1.isHistoricalVersion());
    assertEquals("IC" + container.getId() + "v1", containerV1.getGlobalId());
    assertNull(containerV1.getLocations());

    // a missing revision is a clean 404, not a 200 null body
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                apiKey,
                "/containers/" + container.getId() + "/revisions/999999999",
                anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void versionEndpointsRequireReadPermissionOnLiveRecord() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);

    User otherUser = createInitAndLoginAnyUser();
    String otherApiKey = createNewApiKeyForUser(otherUser);

    // same outcome as trying to read the live record without permission
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE,
                otherApiKey,
                "/samples/" + sample.getId() + "/versions/1",
                otherUser))
        .andExpect(status().is4xxClientError())
        .andReturn();
  }
}
