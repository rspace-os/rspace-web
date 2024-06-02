package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class InventoryBulkOperationsApiControllerTest extends SpringTransactionalTest {

  private @Autowired InventoryBulkOperationsApiController bulkOperationsApi;

  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  @Before
  public void setUp() {
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void createUpdateDeleteRestoreRecordsInBulk() throws BindException {

    // bulk create of sample and a container
    ApiInventoryBulkOperationPost bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.CREATE);
    bulkRequest.setRecords(
        Arrays.asList(
            new ApiSampleWithFullSubSamples("testSample"),
            new ApiContainer("testContainer", ContainerType.LIST)));

    ApiInventoryBulkOperationResult createResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(createResult);
    assertEquals(2, createResult.getSuccessCount());
    assertEquals(0, createResult.getErrorCount());
    ApiSampleWithFullSubSamples createdSample =
        (ApiSampleWithFullSubSamples) createResult.getResults().get(0).getRecord();
    assertEquals("testSample", createdSample.getName());
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);
    ApiContainer createdContainer = (ApiContainer) createResult.getResults().get(1).getRecord();
    assertEquals("testContainer", createdContainer.getName());

    // bulk rename of sample and container
    bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.UPDATE);
    ApiSampleWithFullSubSamples sampleUpdate = new ApiSampleWithFullSubSamples();
    sampleUpdate.setId(createdSample.getId());
    sampleUpdate.setName("my sample");
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(createdContainer.getId());
    containerUpdate.setName("my container");
    List<ApiInventoryRecordInfo> updateRequests = Arrays.asList(sampleUpdate, containerUpdate);
    bulkRequest.setRecords(updateRequests);

    ApiInventoryBulkOperationResult updateResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(updateResult);
    assertEquals(2, updateResult.getSuccessCount());
    assertEquals(0, updateResult.getErrorCount());

    // bulk delete of created sample and container
    bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.DELETE);
    bulkRequest.setRecords(
        Arrays.asList(sampleUpdate, containerUpdate)); // for delete it's enough if id is present

    ApiInventoryBulkOperationResult deleteResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(deleteResult);
    assertEquals(2, deleteResult.getSuccessCount());
    assertEquals(0, deleteResult.getErrorCount());

    // verify state of records after deletion
    ApiSample deletedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals("my sample", deletedSample.getName());
    assertTrue(deletedSample.isDeleted());
    // when retrieving deleted subsample, last active subsamples are also present
    assertEquals(1, deletedSample.getSubSamples().size());
    ApiContainer deletedContainer =
        containerApiMgr.getApiContainerById(createdContainer.getId(), testUser);
    assertEquals("my container", deletedContainer.getName());
    assertTrue(deletedContainer.isDeleted());
    // subsample is deleted as a part of sample deletion
    ApiSubSample deletedSubSample =
        subSampleApiMgr.getApiSubSampleById(createdSubSample.getId(), testUser);
    assertTrue(deletedSubSample.isDeleted());
    assertTrue(deletedSubSample.isDeletedOnSampleDeletion());

    // bulk restore of deleted subsample and container
    bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.RESTORE);
    // for restore it's enough if id is present
    ApiSubSample subSampleUpdate = new ApiSubSample();
    subSampleUpdate.setId(createdSubSample.getId());
    bulkRequest.setRecords(Arrays.asList(subSampleUpdate, containerUpdate));

    ApiInventoryBulkOperationResult restoreResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(restoreResult);
    assertEquals(2, restoreResult.getSuccessCount());
    assertEquals(0, restoreResult.getErrorCount());

    // verify state of records after restore
    ApiSubSample restoredSubSample =
        subSampleApiMgr.getApiSubSampleById(createdSubSample.getId(), testUser);
    assertFalse(restoredSubSample.isDeleted());
    ApiContainer restoredContainer =
        containerApiMgr.getApiContainerById(createdContainer.getId(), testUser);
    assertEquals("my container", restoredContainer.getName());
    assertFalse(restoredContainer.isDeleted());
    // sample also restored, together with subsample
    ApiSample restoredSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals("my sample", restoredSample.getName());
    assertFalse(restoredSample.isDeleted());
  }

  @Test
  public void preOperationValidationWhenRollbackOnErrorTrue() throws BindException {
    // bulk create of 3 samples, 2nd one fails the validation
    ApiInventoryBulkOperationPost bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.CREATE);
    bulkRequest.setRollbackOnError(true);
    ApiSampleWithFullSubSamples okSample = new ApiSampleWithFullSubSamples("okName");
    ApiSampleWithFullSubSamples sampleWithWrongTemp = new ApiSampleWithFullSubSamples("okName");
    sampleWithWrongTemp.setStorageTempMin(
        new ApiQuantityInfo(BigDecimal.valueOf(-1000), RSUnitDef.CELSIUS));
    ApiSampleWithFullSubSamples okSample2 = new ApiSampleWithFullSubSamples("okName2");
    bulkRequest.setRecords(Arrays.asList(okSample, sampleWithWrongTemp, okSample2));

    ApiInventoryBulkOperationResult createResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(createResult);
    assertEquals(0, createResult.getSuccessCount());
    assertEquals(1, createResult.getSuccessCountBeforeFirstError());
    assertEquals(1, createResult.getErrorCount());
    ApiError firstError = createResult.getResults().get(1).getError();
    assertEquals(1, firstError.getErrors().size());
    assertEquals(
        "storageTempMin: Invalid temperature - must be a temperature measurement greater than"
            + " absolute zero",
        firstError.getErrors().get(0));
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, createResult.getStatus());
  }

  @Test
  public void modelValidationCalledDuringBulkMove() throws BindException {
    // bulk create sample and two containers
    ApiSampleWithFullSubSamples okSample = new ApiSampleWithFullSubSamples("okName");
    ApiContainer validContainer = new ApiContainer("myContainer", ContainerType.LIST);
    ApiContainer validSampleContainer = new ApiContainer("mySampleContainer", ContainerType.LIST);
    validSampleContainer.setCanStoreContainers(false);

    ApiInventoryBulkOperationPost bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.CREATE);
    bulkRequest.setRollbackOnError(true);
    bulkRequest.setRecords(Arrays.asList(okSample, validContainer, validSampleContainer));

    ApiInventoryBulkOperationResult createResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(createResult);
    assertEquals(3, createResult.getSuccessCount());
    assertEquals(3, createResult.getSuccessCountBeforeFirstError());
    assertEquals(0, createResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, createResult.getStatus());

    ApiSampleWithFullSubSamples createdSample =
        (ApiSampleWithFullSubSamples) createResult.getResults().get(0).getRecord();
    assertEquals("okName", createdSample.getName());
    ApiContainer createdContainer = (ApiContainer) createResult.getResults().get(1).getRecord();
    assertEquals("myContainer", createdContainer.getName());
    ApiContainer createdSampleContainer =
        (ApiContainer) createResult.getResults().get(2).getRecord();
    assertEquals("mySampleContainer", createdSampleContainer.getName());

    // try bulk move of items into container, ensure model validation is run
    ApiSubSample subSampleMove = new ApiSubSample();
    subSampleMove.setId(createdSample.getSubSamples().get(0).getId());
    subSampleMove.setParentContainer(createdSampleContainer);
    ApiContainer containerMove = new ApiContainer();
    containerMove.setId(createdContainer.getId());
    containerMove.setParentContainer(createdSampleContainer);

    bulkRequest.setOperationType(BulkApiOperationType.MOVE);
    bulkRequest.setRecords(Arrays.asList(subSampleMove, containerMove));
    ApiInventoryBulkOperationResult moveResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(moveResult);
    assertEquals(0, moveResult.getSuccessCount());
    assertEquals(0, moveResult.getSuccessCountBeforeFirstError());
    assertEquals(1, moveResult.getErrorCount());
    ApiError firstError = moveResult.getResults().get(0).getError();
    assertEquals(1, firstError.getErrors().size());
    assertTrue(firstError.getErrors().get(0).contains("can't hold record of type: CONTAINER"));
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, moveResult.getStatus());
  }

  @Test
  public void controllerValidationCalledForIndividualOperations() throws BindException {
    // bulk create of two samples
    ApiInventoryBulkOperationPost bulkRequest = new ApiInventoryBulkOperationPost();
    bulkRequest.setOperationType(BulkApiOperationType.CREATE);
    bulkRequest.setRollbackOnError(false);
    String tooLongDesc = "tooLongDesc" + StringUtils.repeat("x", 250);
    ApiSampleWithFullSubSamples sampleWithTooLongDesc = new ApiSampleWithFullSubSamples("okName");
    sampleWithTooLongDesc.setDescription(tooLongDesc);
    ApiSampleWithFullSubSamples sampleWithNoNameAndTooLongDesc = new ApiSampleWithFullSubSamples();
    sampleWithNoNameAndTooLongDesc.setDescription(tooLongDesc);
    bulkRequest.setRecords(Arrays.asList(sampleWithTooLongDesc, sampleWithNoNameAndTooLongDesc));
    ApiInventoryBulkOperationResult createResult =
        bulkOperationsApi.executeBulkOperation(bulkRequest, mockBindingResult, testUser);
    assertNotNull(createResult);
    assertEquals(0, createResult.getSuccessCount());
    assertEquals(2, createResult.getErrorCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, createResult.getStatus());
    ApiError firstError = createResult.getResults().get(0).getError();
    assertEquals(1, firstError.getErrors().size());
    assertEquals(
        "description: description cannot be greater than 250 characters.",
        firstError.getErrors().get(0));
    ApiError secondError = createResult.getResults().get(1).getError();
    assertEquals(2, secondError.getErrors().size());
    assertEquals("name: name is a required field.", secondError.getErrors().get(0));
    assertEquals(
        "description: description cannot be greater than 250 characters.",
        secondError.getErrors().get(1));
  }

  @Test
  public void bulkMoveSubSamplesWithEditAndMoveOperations() throws BindException {

    // create 3 subsamples
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("mySample");
    ApiSubSample subSample1 = new ApiSubSample("mySubSample1");
    ApiSubSample subSample2 = new ApiSubSample("mySubSample2");
    ApiSubSample subSample3 = new ApiSubSample("mySubSample3");
    newSample.setSubSamples(Arrays.asList(subSample1, subSample2, subSample3));
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    assertEquals(3, createdSample.getSubSamples().size());

    // created subsamples ids
    Long subSampleId1 = createdSample.getSubSamples().get(0).getId();
    Long subSampleId2 = createdSample.getSubSamples().get(1).getId();

    // move to list container with bulk request
    ApiContainer listContainer = createBasicContainerForUser(testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());

    // subsample individual move requests
    ApiSubSample subSampleMove1 = new ApiSubSample();
    subSampleMove1.setId(subSampleId1);
    subSampleMove1.setParentContainer(listContainer);
    ApiSubSample subSampleMove2 = new ApiSubSample();
    subSampleMove2.setId(subSampleId2);
    subSampleMove2.setParentContainer(listContainer);
    List<ApiInventoryRecordInfo> recordsToUpdate = Arrays.asList(subSampleMove1, subSampleMove2);

    // bulk move with UPDATE request
    ApiInventoryBulkOperationPost bulkUpdateRequest = new ApiInventoryBulkOperationPost();
    bulkUpdateRequest.setOperationType(BulkApiOperationType.UPDATE);
    bulkUpdateRequest.setRecords(recordsToUpdate);

    ApiInventoryBulkOperationResult updateResult =
        bulkOperationsApi.executeBulkOperation(bulkUpdateRequest, mockBindingResult, testUser);
    assertEquals(2, updateResult.getSuccessCount());
    assertEquals(0, updateResult.getErrorCount());

    // retrieve container and check its content
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(2, listContainer.getContentSummary().getTotalCount());

    // bulk move with MOVE request
    ApiContainer anotherContainer = createBasicContainerForUser(testUser);
    bulkUpdateRequest.setOperationType(BulkApiOperationType.MOVE);
    subSampleMove1.setParentContainer(anotherContainer);
    subSampleMove2.setParentContainer(anotherContainer);

    updateResult =
        bulkOperationsApi.executeBulkOperation(bulkUpdateRequest, mockBindingResult, testUser);
    assertEquals(2, updateResult.getSuccessCount());
    assertEquals(0, updateResult.getErrorCount());

    // retrieve containers and check their content
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    anotherContainer = containerApiMgr.getApiContainerById(anotherContainer.getId(), testUser);
    assertEquals(2, anotherContainer.getContentSummary().getTotalCount());
  }
}
