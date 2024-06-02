package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.units.RSUnitDef;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MvcResult;

public class SubSamplesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void saveSubsampleImage() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    Long fpCount = getCountOfEntityTable("FileProperty");

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);
    basicSubSampleInfo.setNewBase64Image(BASE_64);
    // now edit
    String toPut =
        String.format(
            "{ \"id\":%d, \"newBase64Image\":\"%s\"}", basicSubSampleInfo.getId(), BASE_64);

    MvcResult updateimage =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    String.format("/subSamples/%d", basicSubSampleInfo.getId()),
                    anyUser,
                    toPut))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSubSample editedSubSample = getFromJsonResponseBody(updateimage, ApiSubSample.class);
    assertEquals(fpCount + 2, getCountOfEntityTable("FileProperty"));
    assertEquals(3, editedSubSample.getLinks().size());
    assertTrue(
        editedSubSample.getLinks().stream()
            .allMatch(ali -> ali.getLink().contains("v1/subSamples")));
  }

  @Test
  public void subsampleHasLinkToSampleImageAsDefault() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);

    // save image on the sample
    String toPut =
        String.format("{ \"id\":%d, \"newBase64Image\":\"%s\"}", basicSample.getId(), BASE_64);

    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, String.format("/samples/%d", basicSample.getId()), anyUser, toPut))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // get the subsample
    MvcResult retrieveSubSampleResult =
        this.mockMvc
            .perform(getSubSampleById(anyUser, apiKey, basicSubSampleInfo.getId()))
            .andReturn();
    ApiSubSample retrievedSubSample =
        getFromJsonResponseBody(retrieveSubSampleResult, ApiSubSample.class);

    assertEquals(3, retrievedSubSample.getLinks().size());
    assertEquals(
        2,
        retrievedSubSample.getLinks().stream()
            .filter(ali -> ali.getLink().contains("v1/samples"))
            .count());
  }

  @Test
  public void addNote() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    Long b4Count = getCountOfEntityTable("SubSampleNote");

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);

    // empty note is rejected
    String newEmptyNote = "{ \"content\" : \"  \" }";
    MvcResult addNewNoteResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    String.format("/subSamples/%d/notes", basicSubSampleInfo.getId()),
                    anyUser,
                    newEmptyNote))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError err = getErrorFromJsonResponseBody(addNewNoteResult, ApiError.class);
    assertApiErrorContainsMessage(err, "empty");
    assertEquals(b4Count, getCountOfEntityTable("SubSampleNote"));

    // happy case
    String newNote = "{ \"content\" : \" my new note \" }";
    addNewNoteResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    String.format("/subSamples/%d/notes", basicSubSampleInfo.getId()),
                    anyUser,
                    newNote))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSubSample editedSubSample = getFromJsonResponseBody(addNewNoteResult, ApiSubSample.class);
    assertEquals(1, editedSubSample.getNotes().size());
    // assert is in Database
    Long afterCount = getCountOfEntityTable("SubSampleNote");
    assertEquals(b4Count + 1, afterCount.intValue());
  }

  @Test
  public void getEditSimpleSubSample() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);
    Long subSampleId = basicSubSampleInfo.getId();
    verifyAuditAction(AuditAction.CREATE, 2);

    // retrieve subsample through API
    MvcResult retrieveSubSampleResult =
        this.mockMvc.perform(getSubSampleById(anyUser, apiKey, subSampleId)).andReturn();
    assertNull(
        retrieveSubSampleResult.getResolvedException(),
        "exception: " + retrieveSubSampleResult.getResolvedException());
    verifyAuditAction(AuditAction.READ, 1);

    ApiSubSample retrievedSubSample =
        getFromJsonResponseBody(retrieveSubSampleResult, ApiSubSample.class);
    assertNotNull(retrievedSubSample);
    assertEquals(subSampleId, retrievedSubSample.getId());
    assertEquals("mySubSample", retrievedSubSample.getName());
    assertNotNull(retrievedSubSample.getSampleInfo());
    assertEquals("mySample", retrievedSubSample.getSampleInfo().getName());
    assertEquals(0, retrievedSubSample.getExtraFields().size());
    assertEquals(5, retrievedSubSample.getQuantity().getNumericValue().longValue());

    // update subsample name and amount - a different, but compatible unit
    int newAmount = 6;
    String updateJson =
        String.format(
            "{ \"name\" : \"newSubSampleName\", \"quantity\" : %s }",
            getQuantityJsonString(newAmount, RSUnitDef.MILLI_GRAM.getId()));
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + subSampleId, anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample editedSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertNotNull(editedSubSample);
    assertEquals("newSubSampleName", editedSubSample.getName());
    assertEquals(newAmount, editedSubSample.getQuantity().getNumericValue().longValue());
    assertEquals(RSUnitDef.MILLI_GRAM.getId(), editedSubSample.getQuantity().getUnitId());
    verifyAuditAction(AuditAction.WRITE, 1);

    //  total sample volume should be updated
    MvcResult retrieveSampleResult =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, basicSample.getId())).andReturn();
    ApiSample sample = getFromJsonResponseBody(retrieveSampleResult, ApiSample.class);
    assertEquals(newAmount, sample.getQuantity().getNumericValue().longValue());
    verifyAuditAction(AuditAction.READ, 2);

    // now try to a temperature unit (incompatible amount unit )
    String updateJson2 =
        String.format("{ \"quantity\" : %s }", getQuantityJsonString(7, RSUnitDef.CELSIUS.getId()));
    MvcResult editResult2 =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + subSampleId, anyUser, updateJson2))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError err = getErrorFromJsonResponseBody(editResult2, ApiError.class);
    assertApiErrorContainsMessage(err, "unit of amount");

    // now try to a volume unit (incompatible with existing mass )
    String updateJson3 =
        String.format(
            "{ \"quantity\" :%s}", getQuantityJsonString(7, RSUnitDef.MILLI_LITRE.getId()));
    MvcResult editResult3 =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + subSampleId, anyUser, updateJson3))
            .andExpect(status().is4xxClientError())
            .andReturn();
    err = getErrorFromJsonResponseBody(editResult3, ApiError.class);
    assertApiErrorContainsMessage(err, "incompatible with stored unit");

    // ensure retrieved Sample has updated subSample metadata
    MvcResult retrievedSampleResult =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, basicSample.getId())).andReturn();
    assertNull(
        retrievedSampleResult.getResolvedException(),
        "exception: " + retrievedSampleResult.getResolvedException());
    ApiSample retrievedSample = getFromJsonResponseBody(retrievedSampleResult, ApiSample.class);
    assertNotNull(retrievedSample);
    assertEquals("newSubSampleName", retrievedSample.getSubSamples().get(0).getName());
    verifyAuditAction(AuditAction.READ, 3);

    Mockito.verifyNoMoreInteractions(auditer);

    // check subsample revision history
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/subSamples/" + subSampleId + "/revisions", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    // check details of 1st revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/subSamples/"
                        + subSampleId
                        + "/revisions/"
                        + history.getRevisions().get(0).getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleRev1Full = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("mySubSample", subSampleRev1Full.getName());
    assertEquals(5, subSampleRev1Full.getQuantity().getNumericValue().longValue());

    // check details of 2nd revision (updated name and different amount)
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/subSamples/"
                        + subSampleId
                        + "/revisions/"
                        + history.getRevisions().get(1).getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleRev2Full = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("newSubSampleName", subSampleRev2Full.getName());
    assertEquals(6, subSampleRev2Full.getQuantity().getNumericValue().longValue());
  }

  private String getQuantityJsonString(int amount, int unitId) {
    return String.format(" {\"numericValue\" : \"%d\", \"unitId\": \"%d\" } ", amount, unitId);
  }

  @Test
  public void moveSubSampleBetweenContainers() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo movingSubSampleInfo = basicSample.getSubSamples().get(0);

    // containers to move around
    ApiContainer listContainer = createBasicContainerForUser(anyUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(anyUser, 4, 4);
    ApiContainer imageContainer = createBasicImageContainerForUser(anyUser);
    verifyAuditAction(AuditAction.CREATE, 5);

    ApiContainer workbench = getWorkbenchForUser(anyUser);
    int initWorkbenchContentCount = workbench.getContentSummary().getTotalCount();
    assertEquals(4, initWorkbenchContentCount); // subsample + 3 containers

    // retrieve subsample through API
    MvcResult retrieveSubSampleResult =
        this.mockMvc
            .perform(getSubSampleById(anyUser, apiKey, movingSubSampleInfo.getId()))
            .andReturn();
    assertNull(
        retrieveSubSampleResult.getResolvedException(),
        "exception: " + retrieveSubSampleResult.getResolvedException());
    ApiSubSample movingSubSample =
        getFromJsonResponseBody(retrieveSubSampleResult, ApiSubSample.class);
    assertNotNull(movingSubSample);
    assertNotNull(movingSubSample.getParentContainer());
    assertEquals(1, movingSubSample.getParentContainers().size());
    assertEquals(workbench.getId(), movingSubSample.getParentContainer().getId());
    assertNull(movingSubSample.getLastNonWorkbenchParent());
    assertNull(movingSubSample.getLastMoveDateMillis());
    verifyAuditAction(AuditAction.READ, 1);

    // move to list container
    String updateJson =
        String.format("{ \"parentContainers\" : [ { \"id\": %d} ] }", listContainer.getId());
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertEquals(listContainer.getId(), movingSubSample.getParentContainer().getId());
    assertEquals(2, movingSubSample.getParentContainers().size());
    assertNull(movingSubSample.getLastNonWorkbenchParent());
    assertNotNull(movingSubSample.getLastMoveDateMillis());
    verifyAuditAction(AuditAction.MOVE, 1);

    // verify source and target container updated
    workbench = getWorkbenchForUser(anyUser);
    assertEquals(initWorkbenchContentCount - 1, workbench.getContentSummary().getTotalCount());
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), anyUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 2);

    // move back to workbench
    updateJson = String.format("{ \"parentContainers\" : [ { \"id\": %d} ] }", workbench.getId());
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertEquals(workbench.getId(), movingSubSample.getParentContainer().getId());
    assertEquals(1, movingSubSample.getParentContainers().size());
    assertEquals(listContainer.getId(), movingSubSample.getLastNonWorkbenchParent().getId());
    assertNotNull(movingSubSample.getLastMoveDateMillis());

    // verify source and target containers updated
    workbench = getWorkbenchForUser(anyUser);
    assertEquals(initWorkbenchContentCount, workbench.getContentSummary().getTotalCount());
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), anyUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 3);

    // move to grid container
    updateJson =
        String.format(
            "{ \"parentContainers\" : [ { \"id\": %d} ],  \"parentLocation\" : "
                + "{ \"coordX\": 2, \"coordY\": 3 } }",
            gridContainer.getId());
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertEquals(gridContainer.getId(), movingSubSample.getParentContainer().getId());
    assertEquals(2, movingSubSample.getParentLocation().getCoordX());
    assertEquals(3, movingSubSample.getParentLocation().getCoordY());
    assertEquals(listContainer.getId(), movingSubSample.getLastNonWorkbenchParent().getId());
    verifyAuditAction(AuditAction.MOVE, 3);

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), anyUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    gridContainer = containerApiMgr.getApiContainerById(gridContainer.getId(), anyUser);
    assertEquals(1, gridContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 5);

    // move to image container
    updateJson =
        String.format(
            "{ \"parentLocation\" : { \"id\": \"%d\"} }",
            imageContainer.getLocations().get(0).getId());
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertEquals(imageContainer.getId(), movingSubSample.getParentContainer().getId());
    verifyAuditAction(AuditAction.MOVE, 4);

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), anyUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), anyUser);
    assertEquals(1, imageContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 7);

    // move back to list container
    updateJson =
        String.format("{ \"parentContainers\" : [ { \"id\": %d} ] }", listContainer.getId());
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    movingSubSample = getFromJsonResponseBody(editResult, ApiSubSample.class);
    assertEquals(listContainer.getId(), movingSubSample.getParentContainer().getId());
    verifyAuditAction(AuditAction.MOVE, 5);

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), anyUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), anyUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 9);

    // try moving into unrelated user's workbench
    User otherUser = createAndSaveUser("otherUser" + getRandomName(6));
    initUser(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);
    updateJson =
        String.format("{ \"parentContainers\" : [ { \"id\": %d} ] }", otherUserWorkbench.getId());
    editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + movingSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "Cannot locate target container");

    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void checkDeletedSubSampleActions() throws Exception {
    Mockito.reset(auditer);

    // create user with a subsample in a container
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    MvcResult getAllResult = getAllSubSamples(anyUser, apiKey, false);
    ApiSubSampleSearchResult allSubSamples =
        getFromJsonResponseBody(getAllResult, ApiSubSampleSearchResult.class);
    assertNotNull(allSubSamples);
    int initialSubSampleCount = allSubSamples.getSubSamples().size();

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);
    ApiContainer basicContainer = createBasicContainerForUser(anyUser);
    verifyAuditAction(AuditAction.CREATE, 3);

    // subsample listed in get all results
    getAllResult = getAllSubSamples(anyUser, apiKey, false);
    allSubSamples = getFromJsonResponseBody(getAllResult, ApiSubSampleSearchResult.class);
    assertNotNull(allSubSamples);
    assertEquals(initialSubSampleCount + 1, allSubSamples.getSubSamples().size());

    // move subsample inside a container
    moveSubSampleIntoListContainer(basicSubSampleInfo.getId(), basicContainer.getId(), anyUser);
    verifyAuditAction(AuditAction.MOVE, 1);

    // verify subsample created and moved
    MvcResult result =
        this.mockMvc
            .perform(getSubSampleById(anyUser, apiKey, basicSubSampleInfo.getId()))
            .andReturn();
    ApiSubSample retrievedSubSample = getFromJsonResponseBody(result, ApiSubSample.class);
    assertNotNull(retrievedSubSample);
    assertFalse(retrievedSubSample.isDeleted());
    assertNotNull(retrievedSubSample.getParentContainer());
    verifyAuditAction(AuditAction.READ, 1);

    // delete subsample
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/subSamples/{id}", anyUser, basicSubSampleInfo.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    verifyAuditAction(AuditAction.DELETE, 1);

    // deleted subsample can be retrieved
    result =
        this.mockMvc
            .perform(getSubSampleById(anyUser, apiKey, basicSubSampleInfo.getId()))
            .andReturn();
    retrievedSubSample = getFromJsonResponseBody(result, ApiSubSample.class);
    assertNotNull(retrievedSubSample);
    assertTrue(retrievedSubSample.isDeleted());
    assertNull(retrievedSubSample.getParentContainer());
    verifyAuditAction(AuditAction.READ, 2);

    // deleted subsample not listed among all subsamples
    getAllResult = getAllSubSamples(anyUser, apiKey, false);
    allSubSamples = getFromJsonResponseBody(getAllResult, ApiSubSampleSearchResult.class);
    assertNotNull(allSubSamples);
    assertEquals(initialSubSampleCount, allSubSamples.getSubSamples().size());

    // but it will show up with include deleted option
    getAllResult = getAllSubSamples(anyUser, apiKey, true);
    allSubSamples = getFromJsonResponseBody(getAllResult, ApiSubSampleSearchResult.class);
    assertNotNull(allSubSamples);
    assertEquals(initialSubSampleCount + 1, allSubSamples.getSubSamples().size());

    // subsample cannot be updated
    String updateJson = String.format("{ \"name\" : \"newSubSampleName\" } ");
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/subSamples/" + basicSubSampleInfo.getId(), anyUser, updateJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "is deleted");

    // notes cannot be added
    String newNote = "{ \"content\" : \" my new note \" }";
    MvcResult addNewNoteResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    String.format("/subSamples/%d/notes", basicSubSampleInfo.getId()),
                    anyUser,
                    newNote))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(addNewNoteResult, ApiError.class);
    assertApiErrorContainsMessage(error, "is deleted");

    // restore the subsample
    result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/subSamples/" + basicSubSampleInfo.getId() + "/restore",
                    anyUser,
                    updateJson))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    retrievedSubSample = getFromJsonResponseBody(result, ApiSubSample.class);
    assertNotNull(retrievedSubSample);
    assertFalse(retrievedSubSample.isDeleted());
    assertTrue(retrievedSubSample.getParentContainer().isWorkbench());
    verifyAuditAction(AuditAction.RESTORE, 1);

    // restored subsample listed again
    getAllResult = getAllSubSamples(anyUser, apiKey, false);
    allSubSamples = getFromJsonResponseBody(getAllResult, ApiSubSampleSearchResult.class);
    assertNotNull(allSubSamples);
    assertEquals(initialSubSampleCount + 1, allSubSamples.getSubSamples().size());

    verifyNoMoreInteractions(auditer);
  }

  private MvcResult getAllSubSamples(User anyUser, String apiKey, boolean includeDeleted)
      throws Exception {
    return mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/subSamples/", anyUser)
                .param("deletedItems", includeDeleted ? "INCLUDE" : null))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  @Test
  public void subSampleRevisionHistory() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(anyUser);
    ApiSubSampleInfo subSample = sample.getSubSamples().get(0);

    // update subsample name
    String updateJson = "{ \"name\" : \"newSubSampleName\" }";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/subSamples/" + subSample.getId(), anyUser, updateJson))
        .andExpect(status().isOk())
        .andReturn();

    // check revision history
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/subSamples/" + subSample.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    ApiInventoryRecordRevision subSampleRev1 = history.getRevisions().get(0);
    assertEquals("mySubSample", subSampleRev1.getRecord().getName());
    assertEquals(3, subSampleRev1.getRecord().getLinks().size());
    assertTrue(
        subSampleRev1
            .getRecord()
            .getLinks()
            .get(0)
            .getLink()
            .endsWith("/revisions/" + subSampleRev1.getRevisionId()));
    ApiInventoryRecordRevision subSampleRev2 = history.getRevisions().get(1);
    assertEquals("newSubSampleName", subSampleRev2.getRecord().getName());

    // check details of first revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/subSamples/"
                        + subSample.getId()
                        + "/revisions/"
                        + subSampleRev1.getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSubSample subSampleRev1Full = getFromJsonResponseBody(result, ApiSubSample.class);
    assertEquals("mySubSample", subSampleRev1Full.getName());
    assertEquals(1, subSampleRev1Full.getExtraFields().size());
    assertEquals(1, subSampleRev1Full.getNotes().size());
    assertEquals("myComplexSample", subSampleRev1Full.getSampleInfo().getName());
    assertEquals(3, subSampleRev1Full.getLinks().size());
    assertTrue(
        subSampleRev1Full
            .getLinks()
            .get(0)
            .getLink()
            .endsWith("/revisions/" + subSampleRev1.getRevisionId()));
  }
}
