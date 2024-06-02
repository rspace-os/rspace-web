package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class InventorySearchApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void findDefaultDevRunSamples() throws Exception {

    User anyUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(anyUser);
    String apiKey = createApiKeyForuser(anyUser);

    // no pagination
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "sample"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundRecords =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(6, foundRecords.getTotalHits().intValue());
    assertEquals(6, foundRecords.getRecords().size());
    assertEquals(1, foundRecords.getLinks().size());

    // wildcard search finding all created samples/subsamples
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "sa*"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(6, foundRecords.getTotalHits().intValue());

    // limit result type to samples
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "sa*")
                    .param("resultType", "SAMPLE"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue());
    assertTrue(foundRecords.getRecords().get(0).getGlobalId().startsWith("SA"));

    // limit result type to subsamples
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "sa*")
                    .param("resultType", "SUBSAMPLE"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue());
    assertTrue(foundRecords.getRecords().get(0).getGlobalId().startsWith("SS"));

    // limit result type to templates
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "sa*")
                    .param("resultType", "TEMPLATE"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue());
    assertTrue(foundRecords.getRecords().get(0).getGlobalId().startsWith("IT"));

    // wildcard search that'll match basic sample & subsample and a few subcontainers
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "b*"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(6, foundRecords.getTotalHits().intValue());
    assertTrue(foundRecords.getRecords().get(0).getGlobalId().startsWith("SA"));
    ApiInventoryRecordInfo foundSubSample = foundRecords.getRecords().get(1);
    ApiContainerInfo foundContainer = (ApiContainerInfo) foundRecords.getRecords().get(2);
    assertTrue(foundSubSample.getGlobalId().startsWith("SS"));
    assertTrue(foundContainer.getGlobalId().startsWith("IC"));
    // search result includes parent container details when listing subsample or subcontainer
    assertEquals("box #1 (list container)", foundSubSample.getParentContainer().getName());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_LIST_CONTAINER_NAME,
        foundContainer.getParentContainer().getName());
    // other fields, needed for ui, are also populated
    assertEquals(anyUser.getFullName(), foundSubSample.getModifiedByFullName());
    assertEquals(anyUser.getFullName(), foundContainer.getModifiedByFullName());
    assertEquals(4, foundContainer.getContentSummary().getTotalCount());
    assertEquals(3, foundContainer.getContentSummary().getContainerCount());
    assertEquals(1, foundContainer.getContentSummary().getSubSampleCount());

    // limit to subcontainers
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "b*")
                    .param("resultType", "CONTAINER"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(4, foundRecords.getTotalHits().intValue());
    assertTrue(foundRecords.getRecords().get(0).getGlobalId().startsWith("IC"));
    assertTrue(foundRecords.getRecords().get(3).getGlobalId().startsWith("IC"));

    // pagination with search - first of three pages listing 'complex' Sample/SubSample/Template
    // matches
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "complex")
                    .param("pageSize", "1")
                    .param("pageNumber", "0"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult paginatedSearchedSamples =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertEquals(3, paginatedSearchedSamples.getTotalHits().intValue());
    assertEquals(1, paginatedSearchedSamples.getRecords().size());
    assertEquals("Complex Sample #1", paginatedSearchedSamples.getRecords().get(0).getName());
    assertEquals(3, paginatedSearchedSamples.getLinks().size());

    // subsample match
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "#1.01"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundSubSamples =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertEquals(1, foundSubSamples.getTotalHits().intValue());
    assertEquals(1, foundSubSamples.getRecords().size());
    assertTrue(foundSubSamples.getRecords().get(0).getGlobalId().startsWith("SS"));
  }

  @Test
  public void searchSampleErrors() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create a sample
    createBasicSampleForUser(anyUser);

    // empty search term
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser).param("query", ""))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals("Empty search term", result.getResolvedException().getMessage());

    // search term too long
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", StringUtils.leftPad("", 126, 'x')))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals(
        "Search term at index 0 is too long (126 chars) - max search string length is 125 chars.",
        result.getResolvedException().getMessage());

    // search term valid but not finding anything
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", StringUtils.leftPad("", 125, 'x')))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult unmatchingSearchResults =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertEquals(0, unmatchingSearchResults.getTotalHits().intValue());

    // invalid record type
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "mySample")
                    .param("resultType", "test"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    String exceptionMessage = result.getResolvedException().getMessage();
    assertTrue(
        exceptionMessage.contains(
            "Requested result type must be SAMPLE, SUBSAMPLE, CONTAINER or TEMPLATE"),
        exceptionMessage);

    // invalid parent global id
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "mySample")
                    .param("parentGlobalId", "SD123"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    exceptionMessage = result.getResolvedException().getMessage();
    assertTrue(
        exceptionMessage.contains(
            "Requested parentGlobalId is incorrect, must be global id of a Container, Workbench,"
                + " Sample, Sample Template, or Basket"),
        exceptionMessage);

    // correct search for sanity check
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "mySample"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult matchingSearchResults =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertEquals(1, matchingSearchResults.getTotalHits().intValue());
  }

  @Test
  public void searchSamplesCreatedFromTemplate() throws Exception {

    User anyUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(anyUser);
    String apiKey = createApiKeyForuser(anyUser);

    ApiSampleSearchResult samplesForUser =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser);
    ApiSampleInfo complexSampleInfo = samplesForUser.getSamples().get(0);
    assertEquals("Complex Sample #1", complexSampleInfo.getName());

    // duplicate the sample, then delete the duplicate
    ApiSampleWithFullSubSamples duplicateSample =
        sampleApiMgr.duplicate(complexSampleInfo.getId(), anyUser);
    sampleApiMgr.markSampleAsDeleted(duplicateSample.getId(), false, anyUser);

    // find all active samples (empty query string) created from complex template
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("parentGlobalId", "IT" + complexSampleInfo.getTemplateId()))
            .andReturn();

    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundRecords =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertEquals(1, foundRecords.getRecords().size());
    assertEquals(complexSampleInfo.getGlobalId(), foundRecords.getRecords().get(0).getGlobalId());
    assertEquals(1, foundRecords.getLinks().size());

    // find all, including deleted, and with template version in global id
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param(
                        "parentGlobalId",
                        "IT"
                            + complexSampleInfo.getTemplateId()
                            + "v"
                            + complexSampleInfo.getTemplateVersion())
                    .param("deletedItems", "INCLUDE"))
            .andReturn();

    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertEquals(2, foundRecords.getRecords().size());
    assertEquals(complexSampleInfo.getGlobalId(), foundRecords.getRecords().get(0).getGlobalId());
    assertEquals(duplicateSample.getGlobalId(), foundRecords.getRecords().get(1).getGlobalId());
    assertEquals(1, foundRecords.getLinks().size());

    // find just deleted
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("parentGlobalId", "IT" + complexSampleInfo.getTemplateId())
                    .param("deletedItems", "DELETED_ONLY"))
            .andReturn();

    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertEquals(duplicateSample.getGlobalId(), foundRecords.getRecords().get(0).getGlobalId());
    assertEquals(1, foundRecords.getLinks().size());
  }

  @Test
  public void searchDeletedSamples() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create two samples and a container
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    ApiContainer container = createBasicContainerForUser(anyUser, "myContainer");

    // mark one of the samples and container as deleted
    sampleApiMgr.markSampleAsDeleted(basicSample.getId(), false, anyUser);
    containerApiMgr.markContainerAsDeleted(container.getId(), anyUser);

    // find active items
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "my*"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundRecords =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue());
    assertEquals(2, foundRecords.getRecords().size());
    assertEquals(complexSample.getGlobalId(), foundRecords.getRecords().get(0).getGlobalId());
    assertEquals(
        complexSample.getSubSamples().get(0).getGlobalId(),
        foundRecords.getRecords().get(1).getGlobalId());
    assertEquals(1, foundRecords.getLinks().size());

    // find deleted items only
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "my*")
                    .param("deletedItems", "DELETED_ONLY"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(3, foundRecords.getTotalHits().intValue());

    // find all items
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", anyUser)
                    .param("query", "my*")
                    .param("deletedItems", "INCLUDE"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(5, foundRecords.getTotalHits().intValue());
  }

  @Test
  public void searchSampleAfterTransferOutsideGroup() throws Exception {

    User firstUser = createInitAndLoginAnyUser();
    String firstApiKey = createApiKeyForuser(firstUser);

    User secondUser = createInitAndLoginAnyUser();
    String secondApiKey = createApiKeyForuser(secondUser);

    // create a sample, move subsample into container
    ApiSampleWithFullSubSamples sample =
        createBasicSampleForUser(firstUser, "transferTest", "transferTest.01", null);
    ApiContainer container = createBasicContainerForUser(firstUser);
    moveSubSampleIntoListContainer(
        sample.getSubSamples().get(0).getId(), container.getId(), firstUser);

    // search for term that should match both sample and subsample
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, firstApiKey, "/search", firstUser)
                    .param("query", "transferTes*"))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundRecords =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue()); // sample and subsample

    // transfer sample to second user
    String sampleUpdateJson =
        "{ \"owner\": { \"username\": \"" + secondUser.getUsername() + "\" } } ";
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    firstApiKey,
                    "/samples/" + sample.getId() + "/actions/changeOwner",
                    firstUser,
                    sampleUpdateJson))
            .andReturn();
    assertNull(editResult.getResolvedException());
    ApiSample editedSample = mvcUtils.getFromJsonResponseBody(editResult, ApiSample.class);
    assertNotNull(editedSample);
    assertEquals(secondUser.getUsername(), editedSample.getOwner().getUsername());

    // execute search as a second user
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, secondApiKey, "/search", secondUser)
                    .param("query", "transferTes*"))
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(2, foundRecords.getTotalHits().intValue());

    // search again as first user
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, firstApiKey, "/search", firstUser)
                    .param("query", "transferTes*"))
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(0, foundRecords.getTotalHits().intValue()); // records were transferred
  }

  @Test
  public void searchForPublicViewItems() throws Exception {

    User firstUser = createInitAndLoginAnyUser();
    String firstApiKey = createApiKeyForuser(firstUser);

    User secondUser = createInitAndLoginAnyUser();
    String secondApiKey = createApiKeyForuser(secondUser);

    // create a sample
    ApiSampleWithFullSubSamples firstUserSample =
        createBasicSampleForUser(firstUser, "publicViewTest", "publicViewTest.01", null);
    ApiSubSample firstUserSubSample = firstUserSample.getSubSamples().get(0);
    // create a container, with a barcode
    ApiContainer firstUserContainer = createBasicContainerForUser(firstUser);
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(firstUserContainer.getId());
    containerUpdate.getBarcodes().add(new ApiBarcode("ABC_BARCODE"));
    containerUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    firstUserContainer = containerApiMgr.updateApiContainer(containerUpdate, firstUser);

    // first user should be able to search and find full items by global ids and barcode
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, firstApiKey, "/search", firstUser)
                    .param("query", firstUserSample.getGlobalId()))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventorySearchResult foundRecords =
        getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertNotNull(foundRecords.getRecords().get(0).getCreatedBy());

    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, firstApiKey, "/search", firstUser)
                    .param("query", firstUserContainer.getBarcodes().get(0).getData()))
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertNotNull(foundRecords.getRecords().get(0).getCreatedBy());

    // second user will just get public view for the same searches
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, secondApiKey, "/search", secondUser)
                    .param("query", firstUserSample.getGlobalId()))
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertNull(foundRecords.getRecords().get(0).getCreatedBy());

    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, secondApiKey, "/search", secondUser)
                    .param("query", firstUserContainer.getBarcodes().get(0).getData()))
            .andReturn();
    assertNull(result.getResolvedException());
    foundRecords = getFromJsonResponseBody(result, ApiInventorySearchResult.class);
    assertNotNull(foundRecords);
    assertEquals(1, foundRecords.getTotalHits().intValue());
    assertNull(foundRecords.getRecords().get(0).getCreatedBy());
  }
}
