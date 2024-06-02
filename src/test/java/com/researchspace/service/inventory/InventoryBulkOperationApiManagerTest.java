package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler.InventoryBulkOperationException;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class InventoryBulkOperationApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;

  private User testUser;

  @Autowired private InventoryMoveHelper moveHelper;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleApiMgr.setPublisher(mockPublisher);
    subSampleApiMgr.setPublisher(mockPublisher);
    containerApiMgr.setPublisher(mockPublisher);
    moveHelper.setPublisher(mockPublisher);
  }

  @Test
  public void bulkCreateSamples() {
    // create request for creating 3 samples, but one of them invalid
    ApiSampleWithFullSubSamples newSample1 = new ApiSampleWithFullSubSamples("mySample1");
    ApiSampleWithFullSubSamples noNameSample = new ApiSampleWithFullSubSamples();
    ApiSampleWithFullSubSamples newSample2 = new ApiSampleWithFullSubSamples("mySample2");
    List<ApiInventoryRecordInfo> records = Arrays.asList(newSample1, noNameSample, newSample2);

    // call with onErrorStopWithException = false should create 2 samples and log the problematic
    // one
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(BulkApiOperationType.CREATE, records, false, testUser);
    ApiInventoryBulkOperationResult bulkResults =
        inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertNotNull(bulkResults);
    assertEquals(2, bulkResults.getSuccessCount());
    assertEquals(newSample1.getName(), bulkResults.getResults().get(0).getRecord().getName());
    assertEquals(newSample2.getName(), bulkResults.getResults().get(2).getRecord().getName());
    assertEquals(1, bulkResults.getErrorCount());
    assertEquals(
        "name: name is a required field.",
        bulkResults.getResults().get(1).getError().getErrors().get(0));

    // call with onErrorStopWithException = true should throw exception after encountering invalid
    // sample definition
    bulkOpConfig.setOnErrorStopWithException(true);
    InventoryBulkOperationException iae =
        assertThrows(
            InventoryBulkOperationException.class,
            () -> inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig));
    assertEquals("name: name is a required field.", iae.getMessage());
  }

  @Test
  public void bulkUpdateSamplesWithStopOnError() {
    List<ApiInventoryRecordInfo> records = createBulkUpdateSubSamples();

    // call update with onErrorStopWithException = true (update should stop with exception on first
    // error)
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(BulkApiOperationType.UPDATE, records, false, testUser);
    bulkOpConfig.setOnErrorStopWithException(true);
    InventoryBulkOperationException boe =
        assertThrows(
            InventoryBulkOperationException.class,
            () -> inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig));
    assertTrue(boe.getMessage().contains("is already taken by the record: SS"), boe.getMessage());
  }

  @Test
  public void bulkUpdateSamplesWithoutStopOnError() {
    List<ApiInventoryRecordInfo> records = createBulkUpdateSubSamples();

    // call update with onErrorStopWithException = false (update should execute for all records)
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(BulkApiOperationType.UPDATE, records, false, testUser);
    ApiInventoryBulkOperationResult result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(1, result.getSuccessCount());
    assertEquals(2, result.getErrorCount());
    assertEquals(
        "No SubSample with id: -1", result.getResults().get(2).getError().getErrors().get(0));
  }

  private List<ApiInventoryRecordInfo> createBulkUpdateSubSamples() {
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(testUser);
    ApiSubSample subSample1 = sample1.getSubSamples().get(0);
    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(testUser);
    ApiSubSample subSample2 = sample2.getSubSamples().get(0);

    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
    ApiContainerLocationWithContent location = imageContainer.getLocations().get(0);

    // create update request that tries move both subsamples into same location, third one that
    // tries moving unexisting subsample
    List<ApiInventoryRecordInfo> records = new ArrayList<>();
    ApiSubSample subSample1Update = new ApiSubSample();
    subSample1Update.setId(subSample1.getId());
    subSample1Update.setParentLocation(location);
    ApiSubSample subSample2Update = new ApiSubSample();
    subSample2Update.setId(subSample2.getId());
    subSample2Update.setParentLocation(location);
    ApiSubSample subSample3Update = new ApiSubSample();
    subSample3Update.setId(-1L);
    subSample3Update.setParentLocation(location);
    records.addAll(Arrays.asList(subSample1Update, subSample2Update, subSample3Update));
    return records;
  }

  @Test
  public void bulkOperationValidation() {

    // create request for creating 3 samples, 2 of them invalid

    // name is enough for a valid sample creation request
    ApiSampleWithFullSubSamples validSample = new ApiSampleWithFullSubSamples("mySample");
    validSample.setNewSampleSubSamplesCount(2);
    // empty name should be caught by SampleApiPostValidator
    ApiSampleWithFullSubSamples noNameSample = new ApiSampleWithFullSubSamples();
    // invalid temperature should be caught by @ValidTemperature annotation
    ApiSampleWithFullSubSamples invalidTempSample = new ApiSampleWithFullSubSamples("wrongTemp");
    invalidTempSample.setStorageTempMin(
        new ApiQuantityInfo(BigDecimal.valueOf(-1000), RSUnitDef.CELSIUS));
    ApiContainer validContainer = new ApiContainer("myContainer", ContainerType.LIST);
    ApiContainer validSampleContainer = new ApiContainer("mySampleContainer", ContainerType.LIST);
    validSampleContainer.setCanStoreContainers(false);

    List<ApiInventoryRecordInfo> records =
        Arrays.asList(
            validSample, noNameSample, invalidTempSample, validContainer, validSampleContainer);

    // call with onErrorStopWithException = false so all operations are executed
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(BulkApiOperationType.CREATE, records, false, testUser);
    ApiInventoryBulkOperationResult bulkResults =
        inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertNotNull(bulkResults);
    assertEquals(3, bulkResults.getSuccessCount());
    assertEquals(validSample.getName(), bulkResults.getResults().get(0).getRecord().getName());
    assertEquals(2, bulkResults.getErrorCount());
    assertEquals(
        "name: name is a required field.",
        bulkResults.getResults().get(1).getError().getErrors().get(0));
    assertEquals(
        "storageTempMin: Invalid temperature - must be a temperature measurement greater than"
            + " absolute zero",
        bulkResults.getResults().get(2).getError().getErrors().get(0));
  }

  @Test
  public void bulkItemMoveAndSwap() {
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(testUser);
    ApiSubSample subSample1 = sample1.getSubSamples().get(0);
    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(testUser);
    ApiSubSample subSample2 = sample2.getSubSamples().get(0);

    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
    ApiContainerLocationWithContent location1 = imageContainer.getLocations().get(0);
    ApiContainerLocationWithContent location2 = imageContainer.getLocations().get(1);
    Mockito.verify(mockPublisher, Mockito.times(5))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    ApiContainerInfo workbench = imageContainer.getParentContainer();
    assertTrue(workbench.isWorkbench());

    // update request moves subsamples into image container
    List<ApiInventoryRecordInfo> records = new ArrayList<>();
    ApiSubSample subSample1Update = new ApiSubSample();
    subSample1Update.setId(subSample1.getId());
    subSample1Update.setParentLocation(location1);
    ApiSubSample subSample2Update = new ApiSubSample();
    subSample2Update.setId(subSample2.getId());
    subSample2Update.setParentLocation(location2);
    records.addAll(Arrays.asList(subSample1Update, subSample2Update));

    // bulk move
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(BulkApiOperationType.MOVE, records, true, testUser);
    ApiInventoryBulkOperationResult result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(2, result.getSuccessCount());
    assertEquals(0, result.getErrorCount());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify container content
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(2, imageContainer.getContentSummary().getTotalCount());
    assertEquals(
        subSample1.getGlobalId(), imageContainer.getLocations().get(0).getContent().getGlobalId());
    assertEquals(
        subSample2.getGlobalId(), imageContainer.getLocations().get(1).getContent().getGlobalId());
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // now try bulk swap
    subSample1Update.setParentLocation(location2);
    subSample2Update.setParentLocation(location1);
    result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(2, result.getSuccessCount());
    assertEquals(0, result.getErrorCount());
    Mockito.verify(mockPublisher, Mockito.times(4))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify container content
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(2, imageContainer.getContentSummary().getTotalCount());
    assertEquals(
        subSample2.getGlobalId(), imageContainer.getLocations().get(0).getContent().getGlobalId());
    assertEquals(
        subSample1.getGlobalId(), imageContainer.getLocations().get(1).getContent().getGlobalId());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // move subsample backs to workbench (RSINV-641)
    subSample1Update.setParentLocation(null);
    subSample1Update.setParentContainer(workbench);
    subSample2Update.setParentLocation(null);
    subSample2Update.setParentContainer(workbench);
    result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(2, result.getSuccessCount());
    assertEquals(0, result.getErrorCount());
    Mockito.verify(mockPublisher, Mockito.times(6))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // move image container to be top-level container (RSINV-641)
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(imageContainer.getId());
    containerUpdate.setRemoveFromParentContainerRequest(true);
    bulkOpConfig.setRecords(Arrays.asList(containerUpdate));
    result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getErrorCount());
    Mockito.verify(mockPublisher, Mockito.times(7))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify image container moved to top-level
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertNull(imageContainer.getParentContainer());
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    // move image container back into workbench
    containerUpdate.setRemoveFromParentContainerRequest(false);
    containerUpdate.setParentContainer(workbench);
    result = inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    assertEquals(1, result.getSuccessCount());
    assertEquals(0, result.getErrorCount());
    Mockito.verify(mockPublisher, Mockito.times(8))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify image container moved back to workbench
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertNotNull(imageContainer.getParentContainer());
    assertEquals(workbench, imageContainer.getParentContainer());
    Mockito.verify(mockPublisher, Mockito.times(4))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }
}
