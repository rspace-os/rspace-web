package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.inventory.Container;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public class InventoryBulkOperationsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void duplicateInventoryItemsInBulk() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(anyUser);
    ApiSampleTemplate template = createBasicSampleTemplate(anyUser);
    final long initialSampleCont = getCountOfEntityTable("Sample");

    String duplicateSampleAndTemplateJSON =
        String.format(
            "{ \"operationType\" : \"%s\", \"records\": [ "
                + "    { \"type\": \"SAMPLE\", \"id\": %d}, "
                + "    { \"type\": \"SAMPLE_TEMPLATE\", \"id\": %d} "
                + "] }",
            BulkApiOperationType.DUPLICATE, sample.getId(), template.getId());
    MvcResult result = postBulkOperation(anyUser, apiKey, duplicateSampleAndTemplateJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getResults().size());
    assertEquals(0, bulkOpResult.getErrorCount());
    assertEquals(
        ApiInventoryRecordType.SAMPLE, bulkOpResult.getResults().get(0).getRecord().getType());
    assertEquals(
        ApiInventoryRecordType.SAMPLE_TEMPLATE,
        bulkOpResult.getResults().get(1).getRecord().getType());
    assertEquals(initialSampleCont + 2, getCountOfEntityTable("Sample"));
  }

  @Test
  public void createEditDeleteInventoryItemsAsBulkOperations() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // batch CREATE - create sample and container
    String createSamplesJSON =
        "{ \"operationType\": \"CREATE\", \"records\": [ { \"type\": \"SAMPLE\","
            + " \"name\":\"bulkSample\" },               { \"type\": \"CONTAINER\", \"cType\":"
            + " \"LIST\", \"name\":\"bulkContainer\" } ] }";
    MvcResult result = postBulkOperation(anyUser, apiKey, createSamplesJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getResults().size());
    assertEquals(0, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkOpResult.getStatus());
    ApiSampleWithFullSubSamples createdSample =
        (ApiSampleWithFullSubSamples) bulkOpResult.getResults().get(0).getRecord();
    assertEquals("bulkSample", createdSample.getName());
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);
    assertEquals("bulkSample.01", createdSubSample.getName());
    ApiContainer createdContainer = (ApiContainer) bulkOpResult.getResults().get(1).getRecord();
    assertEquals("bulkContainer", createdContainer.getName());

    // batch UPDATE - rename sample and container, move created subsample to the container
    String moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"UPDATE\", \"records\": [ { \"type\": \"SAMPLE\", \"id\": %d,"
                + " \"name\": \"renamed sample\" },               { \"type\": \"SUBSAMPLE\","
                + " \"id\": %d, \"parentContainers\": [{ \"id\": %d }] },               { \"type\":"
                + " \"CONTAINER\", \"id\": %d, \"name\": \"renamed container\" } ] }",
            createdSample.getId(),
            createdSubSample.getId(),
            createdContainer.getId(),
            createdContainer.getId());
    result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(3, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());

    // delete created sample and container
    String deleteSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"DELETE\", "
                + "\"records\": [ { \"type\": \"SAMPLE\", \"id\": %d, \"forceDelete\": true }, "
                + "               { \"type\": \"CONTAINER\", \"id\": %d } ] }",
            createdSample.getId(), createdContainer.getId());
    result = postBulkOperation(anyUser, apiKey, deleteSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());

    // check update/delete results
    ApiContainer retrievedContainer =
        containerApiMgr.getApiContainerById(createdContainer.getId(), anyUser);
    assertEquals(0, retrievedContainer.getContentSummary().getTotalCount());
    assertEquals(0, retrievedContainer.getLocations().size());

    // subsample deleted/removed from a container on sample deletion sample
    ApiSubSample retrievedSubSample =
        subSampleApiMgr.getApiSubSampleById(createdSubSample.getId(), anyUser);
    assertTrue(retrievedSubSample.isDeleted());
    assertNotNull(retrievedSubSample.getDeletedDate());
    assertNull(retrievedSubSample.getParentContainer());
  }

  @Test
  public void bulkOperationErrors() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer workbenchForUser = getWorkbenchForUser(anyUser);
    int initWorkbenchCount = workbenchForUser.getContentSummary().getTotalCount();

    ApiSampleSearchResult userSamples = sampleApiMgr.getSamplesForUser(null, null, null, anyUser);
    int initSampleCount = userSamples.getTotalHits().intValue();
    ISearchResults<ApiContainerInfo> userContainers =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, anyUser);
    int initContainerCount = userContainers.getTotalHits().intValue();

    /* invalid request - operation and records not specified */
    String noOperationJSON = "{ }";
    MvcResult result = postBulkOperation(anyUser, apiKey, noOperationJSON);
    assertNotNull(result.getResolvedException());
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Bulk operation must specify operationType"));
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Bulk operation must specify at least 1, at most 100 records"));

    /* invalid request - records null */
    String noRecordsJSON = "{ \"operationType\": \"CREATE\", \"records\": null }";
    result = postBulkOperation(anyUser, apiKey, noRecordsJSON);
    assertNotNull(result.getResolvedException());
    assertTrue(
        result
            .getResolvedException()
            .getMessage()
            .contains("Bulk operation must specify list of records"));

    /* create request but invalid sample data that fails @Valid annotation */
    String wrongTempJSON =
        "{ \"operationType\":\"CREATE\",  \"records\": [ { \"type\": \"SAMPLE\", \"name\": \"My"
            + " ultra-frozen Sample\", \"storageTempMin\" : { \"numericValue\" : -1000.00,"
            + " \"unitId\" : 8 } } ] }";
    result = postBulkOperation(anyUser, apiKey, wrongTempJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(0, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getSuccessCountBeforeFirstError());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, bulkOpResult.getStatus());
    assertEquals(
        "storageTempMin: Invalid temperature - must be a temperature measurement greater than"
            + " absolute zero",
        bulkOpResult.getResults().get(0).getError().getErrors().get(0));

    /* create request for a sample, invalid sample (empty name), and a container */
    String createRecordsTemplateJSON =
        "{ \"operationType\": \"CREATE\", \"rollbackOnError\": %b, \"records\": [ { \"type\":"
            + " \"SAMPLE\", \"name\":\"bulkSample\" },               { \"type\": \"SAMPLE\" },     "
            + "          { \"type\": \"CONTAINER\", \"cType\": \"LIST\", \"name\":\"bulkContainer\""
            + " } ] }";

    // run with default rollbackOnError = true
    String createRecordsStopOnErrorJSON = String.format(createRecordsTemplateJSON, true);
    result = postBulkOperation(anyUser, apiKey, createRecordsStopOnErrorJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(0, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getSuccessCountBeforeFirstError());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, bulkOpResult.getStatus());
    assertEquals(
        "name: name is a required field.",
        bulkOpResult.getResults().get(1).getError().getErrors().get(0));

    // verify records not created
    verifyAuditAction(AuditAction.CREATE, 0);
    userSamples = sampleApiMgr.getSamplesForUser(null, null, null, anyUser);
    assertEquals(initSampleCount, userSamples.getTotalHits().intValue());
    userContainers =
        containerApiMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class), null, null, anyUser);
    assertEquals(initContainerCount, userContainers.getHits());
    // nothing new in workbench
    workbenchForUser = getWorkbenchForUser(anyUser);
    assertEquals(initWorkbenchCount, workbenchForUser.getContentSummary().getTotalCount());

    // run with rollbackOnError = false
    String createRecordsProceedOnErrorJSON = String.format(createRecordsTemplateJSON, false);
    result = postBulkOperation(anyUser, apiKey, createRecordsProceedOnErrorJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getSuccessCountBeforeFirstError());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkOpResult.getStatus());
    assertEquals(
        "name: name is a required field.",
        bulkOpResult.getResults().get(1).getError().getErrors().get(0));

    // verify records created
    verifyAuditAction(AuditAction.CREATE, 3);
    userSamples = sampleApiMgr.getSamplesForUser(null, null, null, anyUser);
    assertEquals(initSampleCount + 1, userSamples.getTotalHits().intValue());
    // container and subsample on workbench now
    workbenchForUser = getWorkbenchForUser(anyUser);
    assertEquals(initWorkbenchCount + 2, workbenchForUser.getContentSummary().getTotalCount());

    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void bulkMoveAndSwap() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // batch CREATE - create sample and container
    String createSamplesJSON =
        "{ \"operationType\": \"CREATE\", \"records\": [ { \"type\": \"SAMPLE\","
            + " \"name\":\"bulkSample\", \"newSampleSubSamplesCount\": 3 },                {"
            + " \"type\": \"CONTAINER\", \"cType\": \"GRID\", \"name\": \"24-well plate\","
            + " \"gridLayout\": { \"columnsNumber\": 6, \"rowsNumber\": 4} } ] }";
    MvcResult result = postBulkOperation(anyUser, apiKey, createSamplesJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getResults().size());
    assertEquals(0, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkOpResult.getStatus());
    ApiSampleWithFullSubSamples createdSample =
        (ApiSampleWithFullSubSamples) bulkOpResult.getResults().get(0).getRecord();
    ApiSubSample subSample1 = createdSample.getSubSamples().get(0);
    ApiSubSample subSample2 = createdSample.getSubSamples().get(1);
    ApiSubSample subSample3 = createdSample.getSubSamples().get(2);
    ApiContainer gridContainer = (ApiContainer) bulkOpResult.getResults().get(1).getRecord();
    verifyAuditAction(AuditAction.CREATE, 5);
    // subsamples and containers are created in workbench by default
    assertNotNull(subSample1.getParentContainer());
    assertEquals("WORKBENCH", subSample1.getParentContainer().getCType());
    assertNull(subSample1.getLastMoveDateMillis());
    assertEquals("WORKBENCH", gridContainer.getParentContainer().getCType());
    assertNull(gridContainer.getLastMoveDateMillis());

    // batch MOVE - put subsamples into container
    String moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 2,"
                + " \"coordY\": 3 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 2,"
                + " \"coordY\": 4 } }   ] }",
            subSample1.getId(), gridContainer.getId(), subSample2.getId(), gridContainer.getId());
    result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());
    verifyAuditAction(AuditAction.MOVE, 2);

    // check move results
    ApiContainer retrievedContainer =
        containerApiMgr.getApiContainerById(gridContainer.getId(), anyUser);
    assertEquals(2, retrievedContainer.getContentSummary().getTotalCount());
    assertEquals(2, retrievedContainer.getLocations().size());
    ApiSubSample retrievedSubSample1 =
        subSampleApiMgr.getApiSubSampleById(subSample1.getId(), anyUser);
    assertEquals(retrievedContainer.getId(), retrievedSubSample1.getParentContainer().getId());
    assertEquals(2, retrievedSubSample1.getParentLocation().getCoordX());
    assertEquals(3, retrievedSubSample1.getParentLocation().getCoordY());
    assertNotNull(retrievedSubSample1.getLastMoveDateMillis());
    ApiSubSample retrievedSubSample2 =
        subSampleApiMgr.getApiSubSampleById(subSample2.getId(), anyUser);
    assertEquals(retrievedContainer.getId(), retrievedSubSample2.getParentContainer().getId());
    assertEquals(2, retrievedSubSample2.getParentLocation().getCoordX());
    assertEquals(4, retrievedSubSample2.getParentLocation().getCoordY());
    assertNotNull(retrievedSubSample2.getLastMoveDateMillis());
    // 3-rd subsample stays in workbench
    ApiSubSample retrievedSubSample3 =
        subSampleApiMgr.getApiSubSampleById(subSample3.getId(), anyUser);
    assertEquals("WORKBENCH", retrievedSubSample3.getParentContainer().getCType());
    assertNull(retrievedSubSample3.getLastMoveDateMillis());
    verifyAuditAction(AuditAction.READ, 4);

    // batch MOVE - swap existing subsamples in container, add move third one
    moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 2,"
                + " \"coordY\": 4 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 2,"
                + " \"coordY\": 3 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 3,"
                + " \"coordY\": 3 } }   ] }",
            subSample1.getId(),
            gridContainer.getId(),
            subSample2.getId(),
            gridContainer.getId(),
            subSample3.getId(),
            gridContainer.getId());
    result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(3, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());
    verifyAuditAction(AuditAction.MOVE, 5);

    // check move&swap results
    retrievedContainer = containerApiMgr.getApiContainerById(gridContainer.getId(), anyUser);
    assertEquals(3, retrievedContainer.getContentSummary().getTotalCount());
    assertEquals(3, retrievedContainer.getLocations().size());
    retrievedSubSample1 = subSampleApiMgr.getApiSubSampleById(subSample1.getId(), anyUser);
    assertEquals(2, retrievedSubSample1.getParentLocation().getCoordX());
    assertEquals(4, retrievedSubSample1.getParentLocation().getCoordY());
    retrievedSubSample2 = subSampleApiMgr.getApiSubSampleById(subSample2.getId(), anyUser);
    assertEquals(2, retrievedSubSample2.getParentLocation().getCoordX());
    assertEquals(3, retrievedSubSample2.getParentLocation().getCoordY());
    retrievedSubSample3 = subSampleApiMgr.getApiSubSampleById(subSample3.getId(), anyUser);
    assertEquals(3, retrievedSubSample3.getParentLocation().getCoordX());
    assertEquals(3, retrievedSubSample3.getParentLocation().getCoordY());
    assertNotNull(retrievedSubSample3.getLastMoveDateMillis());
    verifyAuditAction(AuditAction.READ, 8);

    // batch MOVE - try moving first two subsamples one row down in the grid (should return an error
    // as third subsample is blocking target location)
    moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 3,"
                + " \"coordY\": 4 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 3,"
                + " \"coordY\": 3 } }   ] }",
            subSample1.getId(),
            gridContainer.getId(),
            subSample2.getId(),
            gridContainer.getId(),
            subSample3.getId(),
            gridContainer.getId());
    result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(0, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, bulkOpResult.getStatus());
    assertTrue(
        bulkOpResult
            .getResults()
            .get(0)
            .getError()
            .getErrors()
            .get(0)
            .contains("is already taken by the record"));

    // confirm subsamples not moved
    retrievedSubSample1 = subSampleApiMgr.getApiSubSampleById(subSample1.getId(), anyUser);
    assertEquals(2, retrievedSubSample1.getParentLocation().getCoordX());
    assertEquals(4, retrievedSubSample1.getParentLocation().getCoordY());
    verifyAuditAction(AuditAction.READ, 9);

    // batch MOVE - move all subsamples one row down in the grid (second subsample goes into place
    // initally occupied by third subsample)
    moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 3,"
                + " \"coordY\": 4 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 3,"
                + " \"coordY\": 3 } },  { \"type\": \"SUBSAMPLE\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }], \"parentLocation\" : { \"coordX\": 4,"
                + " \"coordY\": 3 } }   ] }",
            subSample1.getId(),
            gridContainer.getId(),
            subSample2.getId(),
            gridContainer.getId(),
            subSample3.getId(),
            gridContainer.getId());
    result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(3, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());
    verifyAuditAction(AuditAction.MOVE, 8);

    // check move&swap results
    retrievedContainer = containerApiMgr.getApiContainerById(gridContainer.getId(), anyUser);
    assertEquals(3, retrievedContainer.getContentSummary().getTotalCount());
    assertEquals(3, retrievedContainer.getLocations().size());
    retrievedSubSample1 = subSampleApiMgr.getApiSubSampleById(subSample1.getId(), anyUser);
    assertEquals(3, retrievedSubSample1.getParentLocation().getCoordX());
    assertEquals(4, retrievedSubSample1.getParentLocation().getCoordY());
    retrievedSubSample2 = subSampleApiMgr.getApiSubSampleById(subSample2.getId(), anyUser);
    assertEquals(3, retrievedSubSample2.getParentLocation().getCoordX());
    assertEquals(3, retrievedSubSample2.getParentLocation().getCoordY());
    retrievedSubSample3 = subSampleApiMgr.getApiSubSampleById(subSample3.getId(), anyUser);
    assertEquals(4, retrievedSubSample3.getParentLocation().getCoordX());
    assertEquals(3, retrievedSubSample3.getParentLocation().getCoordY());
    verifyAuditAction(AuditAction.READ, 13);

    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void bulkMoveChecksPermissions() throws Exception {

    User testUser = createInitAndLoginAnyUser();
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);
    ApiContainer testUserContainer = createBasicContainerForUser(testUser);
    String apiKey = createNewApiKeyForUser(testUser);

    User otherUser = createAndSaveUser(getRandomName(10));
    initUsers(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);
    ApiContainer otherUserContainer = createBasicContainerForUser(otherUser);

    // batch MOVE - try moving container owned by unrelated user's to own bench
    String moveContainerJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"CONTAINER\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }] } ] }",
            otherUserContainer.getId(), testUserWorkbench.getId());
    MvcResult result = postBulkOperation(testUser, apiKey, moveContainerJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(0, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, bulkOpResult.getStatus());
    String errorMsg = bulkOpResult.getResults().get(0).getError().getErrors().get(0);
    assertTrue(errorMsg.contains("could not be retrieved"), errorMsg);

    // confirm container not moved
    ApiContainer retrievedContainer =
        containerApiMgr.getApiContainerById(otherUserContainer.getId(), otherUser);
    assertEquals(
        otherUserWorkbench.getGlobalId(), retrievedContainer.getParentContainer().getGlobalId());

    // batch MOVE - try moving own container into unrelated user's bench
    moveContainerJSON =
        String.format(
            "{ \"operationType\" : \"MOVE\", \"records\": [  { \"type\": \"CONTAINER\", \"id\": %d,"
                + " \"parentContainers\": [{ \"id\": %d }] } ] }",
            testUserContainer.getId(), otherUserWorkbench.getId());
    result = postBulkOperation(testUser, apiKey, moveContainerJSON);
    assertNull(result.getResolvedException());
    bulkOpResult = getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(0, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, bulkOpResult.getStatus());
    errorMsg = bulkOpResult.getResults().get(0).getError().getErrors().get(0);
    assertTrue(errorMsg.contains("move.failure.cannot.locate.target.container"), errorMsg);

    // confirm container not moved
    retrievedContainer = containerApiMgr.getApiContainerById(testUserContainer.getId(), testUser);
    assertEquals(
        testUserWorkbench.getGlobalId(), retrievedContainer.getParentContainer().getGlobalId());
  }

  @Test
  public void bulkTransferOfInventoryItems() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    // create a container and a sample
    ApiContainer createdContainer = createBasicContainerForUser(anyUser);
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(anyUser);
    ApiSubSample createdSubSample = createBasicSampleForUser(anyUser).getSubSamples().get(0);

    // create another user
    String otherUsername = doCreateAndInitUser("otherBulkUser").getUsername();

    // batch change owner
    String moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"CHANGE_OWNER\", \"rollbackOnError\": false, \"records\": [ {"
                + " \"type\": \"SAMPLE\", \"id\": %d, \"owner\": { \"username\": \"%s\" } },       "
                + "        { \"type\": \"SUBSAMPLE\", \"id\": %d, \"owner\": { \"username\": \"%s\""
                + " } },               { \"type\": \"CONTAINER\", \"id\": %d, \"owner\": {"
                + " \"username\": \"%s\" } } ] }",
            createdSample.getId(),
            otherUsername,
            createdSubSample.getId(),
            otherUsername,
            createdContainer.getId(),
            otherUsername);
    MvcResult result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkOpResult.getStatus());
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(1, bulkOpResult.getErrorCount());
    assertEquals(
        otherUsername, bulkOpResult.getResults().get(0).getRecord().getOwner().getUsername());
    assertEquals(
        "bulk owner change doesn't support records of type: SUBSAMPLE",
        bulkOpResult.getResults().get(1).getError().getErrors().get(0));
    assertEquals(
        otherUsername, bulkOpResult.getResults().get(2).getRecord().getOwner().getUsername());
  }

  @Test
  public void bulkPermissionChangeForInventoryItems() throws Exception {
    // create a user, and a group
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    initUsers(pi);
    Group group = createGroupForUsersWithDefaultPi(pi, anyUser);

    // create a sample and a container
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(anyUser);
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS,
        createdSample.getSharingMode());
    assertEquals(1, createdSample.getSharedWith().size());
    assertFalse(createdSample.getSharedWith().get(0).isShared());
    ApiContainer createdContainer = createBasicContainerForUser(anyUser);
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS,
        createdContainer.getSharingMode());
    assertEquals(1, createdContainer.getSharedWith().size());
    assertFalse(createdContainer.getSharedWith().get(0).isShared());

    // batch UPDATE of permissions
    String moveSubSamplesJSON =
        String.format(
            "{ \"operationType\" : \"UPDATE\", \"rollbackOnError\": false, \"records\": [ {"
                + " \"type\": \"SAMPLE\", \"id\": %d, \"sharingMode\": \"OWNER_ONLY\","
                + " \"sharedWith\": [] },               { \"type\": \"CONTAINER\", \"id\": %d,"
                + " \"sharingMode\": \"WHITELIST\",                  \"sharedWith\": [ { \"group\":"
                + " { \"id\": \"%d\" }, \"shared\": true } ] }              ] }",
            createdSample.getId(), createdContainer.getId(), group.getId());
    MvcResult result = postBulkOperation(anyUser, apiKey, moveSubSamplesJSON);
    assertNull(result.getResolvedException());
    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkOpResult.getStatus());
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());

    // assert permissions updated
    ApiInventoryRecordInfo updatedSample = bulkOpResult.getResults().get(0).getRecord();
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_ONLY, updatedSample.getSharingMode());
    assertEquals(1, updatedSample.getSharedWith().size());
    assertFalse(updatedSample.getSharedWith().get(0).isShared());
    ApiInventoryRecordInfo updatedContainer = bulkOpResult.getResults().get(1).getRecord();
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST,
        updatedContainer.getSharingMode());
    assertEquals(1, updatedContainer.getSharedWith().size());
    assertTrue(updatedContainer.getSharedWith().get(0).isShared());
  }

  private MvcResult postBulkOperation(User anyUser, String apiKey, String detailsJSON)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/bulk", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(detailsJSON))
            .andReturn();
    return result;
  }
}
