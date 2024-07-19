package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

public class SampleApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;

  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    sampleDao.resetDefaultTemplateOwner();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleApiMgr.setPublisher(mockPublisher);
    subSampleApiMgr.setPublisher(mockPublisher);
  }

  @Test
  public void sampleRetrievalAndPagination() {
    List<ApiInventoryRecordInfo> createdSamples =
        createMultipleSamplesForUser("mySample", 15, testUser);
    assertEquals(15, createdSamples.size());
    Mockito.verify(mockPublisher, Mockito.times(30))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    // default pagination criteria
    ApiSampleSearchResult defaultSamplesResult =
        sampleApiMgr.getSamplesForUser(null, null, null, testUser);
    assertEquals(15, defaultSamplesResult.getTotalHits().intValue());
    assertEquals(10, defaultSamplesResult.getSamples().size());
    assertEquals(0, defaultSamplesResult.getPageNumber().intValue());
    assertEquals("mySample-15", defaultSamplesResult.getSamples().get(0).getName());

    // try retrieving all samples
    PaginationCriteria<Sample> pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    pgCrit.setGetAllResults();
    ApiSampleSearchResult allSamplesResult =
        sampleApiMgr.getSamplesForUser(pgCrit, null, null, testUser);
    assertEquals(15, allSamplesResult.getTotalHits().intValue());
    assertEquals(15, allSamplesResult.getSamples().size());
    assertEquals(0, allSamplesResult.getPageNumber().intValue());
    assertEquals("mySample-15", allSamplesResult.getSamples().get(0).getName());

    // try retrieving second page of samples
    pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    pgCrit.setPageNumber(1L);
    pgCrit.setResultsPerPage(5);
    pgCrit.setSortOrder(SortOrder.ASC);
    ApiSampleSearchResult secondPageResult =
        sampleApiMgr.getSamplesForUser(pgCrit, null, null, testUser);
    assertEquals(15, secondPageResult.getTotalHits().intValue());
    assertEquals(5, secondPageResult.getSamples().size());
    assertEquals(1, secondPageResult.getPageNumber().intValue());
    assertEquals("mySample-06", secondPageResult.getSamples().get(0).getName());

    // check ordering by global id (desc)
    pgCrit.setOrderBy(SearchUtils.ORDER_BY_GLOBAL_ID);
    pgCrit.setSortOrder(SortOrder.DESC);
    secondPageResult = sampleApiMgr.getSamplesForUser(pgCrit, null, null, testUser);
    assertEquals(15, secondPageResult.getTotalHits().intValue());
    assertEquals(5, secondPageResult.getSamples().size());
    assertEquals(1, secondPageResult.getPageNumber().intValue());
    assertEquals("mySample-10", secondPageResult.getSamples().get(0).getName());

    // listing samples doesn't result in audit events
    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void saveUpdateNewBasicSample() throws InterruptedException {
    ApiSampleWithFullSubSamples newSample = createBasicSampleForUser(testUser);
    assertNotNull(newSample);
    assertEquals(3, newSample.getPermittedActions().size());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals(newSample.getName(), retrievedSample.getName());
    assertNotNull(retrievedSample.getSubSamples());
    assertEquals(1, retrievedSample.getVersion());
    assertEquals(3, retrievedSample.getPermittedActions().size());
    assertEquals(0, retrievedSample.getFields().size());
    assertEquals(0, retrievedSample.getExtraFields().size());
    assertEquals(1, retrievedSample.getSubSamples().size());
    assertEquals(1, retrievedSample.getSubSamplesCount());
    ApiSubSampleInfo retrievedSubSample = retrievedSample.getSubSamples().get(0);
    assertEquals("mySubSample", retrievedSubSample.getName());
    // subsample stays in workbench
    assertNotNull(retrievedSubSample.getParentContainer());
    assertEquals("WORKBENCH", retrievedSubSample.getParentContainer().getCType());
    assertNull(retrievedSubSample.getLastMoveDateMillis());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(retrievedSample.getId());
    sampleUpdates.setName("updated name");
    sampleUpdates.setDescription("updated description");
    sampleUpdates.setApiTagInfo("updated tags");
    sampleUpdates.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.CELSIUS));
    sampleUpdates.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(15), RSUnitDef.CELSIUS));

    Thread.sleep(10); // ensure it's later
    ApiSample updatedSample = sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    assertNotNull(updatedSample);
    assertEquals(retrievedSample.getGlobalId(), updatedSample.getGlobalId());
    assertEquals("updated name", updatedSample.getName());
    assertTrue(updatedSample.getLastModifiedMillis() > retrievedSample.getLastModifiedMillis());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));

    ApiSampleSearchResult userSamples = getUserSamples(false);
    assertNotNull(userSamples);
    assertEquals(1, userSamples.getTotalHits().intValue());
    ApiSampleInfo userSample = userSamples.getSamples().get(0);
    assertEquals(retrievedSample.getGlobalId(), userSample.getGlobalId());
    assertEquals(testUser.getFullName(), userSample.getModifiedByFullName());
    assertEquals("updated name", userSample.getName());
    assertEquals("updated description", userSample.getDescription());
    assertEquals("updated tags", userSample.getTags().get(0).toString());
    assertEquals(3, retrievedSample.getPermittedActions().size());
    assertEquals(2, userSample.getVersion());
    assertEquals(sampleUpdates.getStorageTempMin(), userSample.getStorageTempMin());
    assertEquals(sampleUpdates.getStorageTempMax(), userSample.getStorageTempMax());
    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  private ApiSampleSearchResult getUserSamples(boolean includeDeleted) {
    PaginationCriteria<Sample> pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    pgCrit.setGetAllResults();
    InventorySearchDeletedOption deletedOption =
        includeDeleted
            ? InventorySearchDeletedOption.INCLUDE
            : InventorySearchDeletedOption.EXCLUDE;
    return sampleApiMgr.getSamplesForUser(pgCrit, null, deletedOption, testUser);
  }

  @Test
  public void saveUpdateComplexSample() {
    ApiSampleWithFullSubSamples newSample = createComplexSampleForUser(testUser);
    assertNotNull(newSample);

    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals("myComplexSample", retrievedSample.getName());
    assertNotNull(retrievedSample.getSubSamples());
    assertEquals(9, retrievedSample.getFields().size());
    assertEquals("23", retrievedSample.getFields().get(0).getContent());
    assertEquals(1, retrievedSample.getExtraFields().size());
    assertEquals("3.14", retrievedSample.getExtraFields().get(0).getContent());
    assertEquals(1, retrievedSample.getSubSamples().size());
    assertEquals(1, retrievedSample.getSubSamplesCount());
    assertEquals(1, retrievedSample.getTags().size());
    ApiSubSampleInfo retrievedSubSample = retrievedSample.getSubSamples().get(0);
    assertEquals("mySubSample", retrievedSubSample.getName());

    ApiSubSample fullRetrievedSubSample =
        subSampleApiMgr.getApiSubSampleById(retrievedSubSample.getId(), testUser);
    assertEquals(1, fullRetrievedSubSample.getExtraFields().size());
    assertEquals(1, fullRetrievedSubSample.getNotes().size());

    // prepare sample update
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(retrievedSample.getId());
    sampleUpdates.setName("updated myComplexSample");
    /* unfortunately tags list is special & needs to be set every time to not get cleared */
    sampleUpdates.setTags(retrievedSample.getTags());

    // modification to content of sample field should be accepted
    ApiSampleField fieldUpdate = new ApiSampleField();
    fieldUpdate.setId(retrievedSample.getFields().get(0).getId());
    fieldUpdate.setContent("24");
    sampleUpdates.getFields().add(fieldUpdate);

    // attempt to add sample field - back-end should ignore
    ApiSampleField newField =
        createBasicApiSampleField("new field", ApiFieldType.STRING, "test content");
    newField.setNewFieldRequest(true);
    sampleUpdates.getFields().add(newField);

    // attempts to delete sample fields - back-end should ignore
    ApiSampleField fieldDelete1 = new ApiSampleField();
    fieldDelete1.setId(retrievedSample.getFields().get(1).getId());
    fieldDelete1.setDeleteFieldRequest(true);
    sampleUpdates.getFields().add(fieldDelete1);
    ApiSampleField fieldDelete2 = new ApiSampleField();
    fieldDelete2.setId(retrievedSample.getFields().get(2).getId());
    fieldDelete2.setDeleteFieldRequest(true);
    sampleUpdates.getFields().add(fieldDelete2);

    // extra fields can be added/deleted fine though
    ApiExtraField extraFieldUpdate = new ApiExtraField();
    extraFieldUpdate.setId(retrievedSample.getExtraFields().get(0).getId());
    extraFieldUpdate.setContent("3.15");
    sampleUpdates.getExtraFields().add(extraFieldUpdate);

    // new extra field
    ApiExtraField newExtraField = new ApiExtraField();
    newExtraField.setNewFieldRequest(true);
    newExtraField.setContent("new field content");
    sampleUpdates.getExtraFields().add(newExtraField);

    // run the update & retrieve updated sample
    sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals("updated myComplexSample", retrievedSample.getName());
    assertEquals(9, retrievedSample.getFields().size()); // same number of sample fields
    assertEquals("24", retrievedSample.getFields().get(0).getContent());
    assertEquals(2, retrievedSample.getExtraFields().size()); // increased number of extra fields
    assertEquals("3.15", retrievedSample.getExtraFields().get(0).getContent());
    assertEquals("new field content", retrievedSample.getExtraFields().get(1).getContent());
    assertEquals(1, retrievedSample.getTags().size()); // the unmodified tag is still there
  }

  @Test
  public void saveSampleQuantities() {
    ApiQuantityInfo apiQuantity5ML =
        new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.MILLI_LITRE);
    ApiQuantityInfo apiQuantity2L = new ApiQuantityInfo(BigDecimal.valueOf(2), RSUnitDef.LITRE);
    ApiQuantityInfo apiQuantity2L5ML =
        new ApiQuantityInfo(BigDecimal.valueOf(2.005), RSUnitDef.LITRE);

    // 1. default sample with specified quantity, no specified subsamples
    ApiSampleWithFullSubSamples defaultSample = new ApiSampleWithFullSubSamples();
    defaultSample.setName("mySample");
    defaultSample.setQuantity(apiQuantity5ML);

    // ... quantity provided for sample should be assigned to default subsample
    ApiSampleWithFullSubSamples createdDefaultSample =
        sampleApiMgr.createNewApiSample(defaultSample, testUser);
    assertEquals(apiQuantity5ML, createdDefaultSample.getQuantity());
    assertEquals(apiQuantity5ML, createdDefaultSample.getSubSamples().get(0).getQuantity());

    // 2. default sample with specified quantity, one subsample provided without specified quantity
    ApiSampleWithFullSubSamples sampleWithSubSample = new ApiSampleWithFullSubSamples();
    sampleWithSubSample.setName("mySample");
    sampleWithSubSample.setQuantity(apiQuantity5ML);
    ApiSubSample apiSubSample = new ApiSubSample();
    apiSubSample.setName("mySubSample");
    sampleWithSubSample.setSubSamples(List.of(apiSubSample));

    // ... sample total should set subsample quantity
    ApiSampleWithFullSubSamples createdSampleWithSubSample =
        sampleApiMgr.createNewApiSample(sampleWithSubSample, testUser);
    assertEquals(apiQuantity5ML, createdSampleWithSubSample.getQuantity());
    assertEquals(apiQuantity5ML, createdSampleWithSubSample.getSubSamples().get(0).getQuantity());
    assertEquals(
        apiSubSample.getName(), createdSampleWithSubSample.getSubSamples().get(0).getName());

    // 3. default sample with specified quantity, one subsample with specified
    sampleWithSubSample = new ApiSampleWithFullSubSamples();
    sampleWithSubSample.setQuantity(apiQuantity5ML);
    apiSubSample = new ApiSubSample();
    apiSubSample.setQuantity(apiQuantity2L);
    sampleWithSubSample.setSubSamples(List.of(apiSubSample));

    // ... should recalculate sample quantity from provided subsample
    createdSampleWithSubSample = sampleApiMgr.createNewApiSample(sampleWithSubSample, testUser);
    assertEquals(apiQuantity2L, createdSampleWithSubSample.getQuantity());
    assertEquals(apiQuantity2L, createdSampleWithSubSample.getSubSamples().get(0).getQuantity());

    // 4. default sample with specified quantity, multiple subsamples with specified quantity
    ApiSampleWithFullSubSamples sampleWithMultipleSubSamples = new ApiSampleWithFullSubSamples();
    sampleWithMultipleSubSamples.setName("mySample");
    sampleWithMultipleSubSamples.setQuantity(apiQuantity2L);
    ApiSubSample apiSubSample1 = new ApiSubSample();
    apiSubSample1.setName("mySubSample #1");
    apiSubSample1.setQuantity(apiQuantity2L);
    ApiSubSample apiSubSample2 = new ApiSubSample();
    apiSubSample2.setName("mySubSample #1");
    apiSubSample2.setQuantity(apiQuantity5ML);
    sampleWithMultipleSubSamples.setSubSamples(List.of(apiSubSample1, apiSubSample2));

    // ... should recalculate sample quantity from provided multiple subsamples
    ApiSampleWithFullSubSamples createdSampleWithMultipleSubSamples =
        sampleApiMgr.createNewApiSample(sampleWithMultipleSubSamples, testUser);
    assertEquals(apiQuantity2L5ML, createdSampleWithMultipleSubSamples.getQuantity());
    assertEquals(
        apiQuantity2L, createdSampleWithMultipleSubSamples.getSubSamples().get(0).getQuantity());
    assertEquals(
        apiQuantity5ML, createdSampleWithMultipleSubSamples.getSubSamples().get(1).getQuantity());

    // 5. default sample with specified quantity, multiple subsamples without specified quantity
    sampleWithMultipleSubSamples = new ApiSampleWithFullSubSamples();
    sampleWithMultipleSubSamples.setQuantity(apiQuantity2L);
    sampleWithMultipleSubSamples.setSubSamples(List.of(new ApiSubSample(), new ApiSubSample()));

    // ... should recalculate sample quantity from multiple subsamples defaults
    createdSampleWithMultipleSubSamples =
        sampleApiMgr.createNewApiSample(sampleWithMultipleSubSamples, testUser);
    assertEquals(
        "2 ml", createdSampleWithMultipleSubSamples.getQuantity().toQuantityInfo().toPlainString());
    assertNotNull(createdSampleWithMultipleSubSamples.getSubSamples().get(0).getQuantity());
    assertNotNull(createdSampleWithMultipleSubSamples.getSubSamples().get(1).getQuantity());
  }

  @Test
  public void saveUpdateDeleteSampleIdentifier() {
    ApiSampleWithFullSubSamples newSample = createBasicSampleForUser(testUser);
    assertNotNull(newSample);

    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals("mySample", retrievedSample.getName());
    assertEquals(0, retrievedSample.getIdentifiers().size());

    // prepare sample update that adds an identifier
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(retrievedSample.getId());
    ApiInventoryDOI newIdentifier = new ApiInventoryDOI();
    newIdentifier.setRegisterIdentifierRequest(true);
    newIdentifier.setTitle("testDOItitle");
    newIdentifier.setPublisher("ResearchSpace");
    sampleUpdates.getIdentifiers().add(newIdentifier);

    // run the update & retrieve updated sample
    sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals(1, retrievedSample.getIdentifiers().size());
    ApiInventoryDOI createdIdentifier = retrievedSample.getIdentifiers().get(0);
    assertNotNull(createdIdentifier.getId());
    assertEquals("testDOItitle", createdIdentifier.getTitle());
    assertEquals("ResearchSpace", createdIdentifier.getPublisher());
    assertNull(createdIdentifier.getDoi());

    // update pre-existing identifier
    sampleUpdates = new ApiSample();
    sampleUpdates.setId(retrievedSample.getId());
    ApiInventoryDOI identifierUpdate = new ApiInventoryDOI();
    identifierUpdate.setId(createdIdentifier.getId());
    identifierUpdate.setDoi("abcDOI");
    sampleUpdates.getIdentifiers().add(identifierUpdate);

    // run the update
    sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    retrievedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertEquals(1, retrievedSample.getIdentifiers().size());
    ApiInventoryDOI updatedIdentifier = retrievedSample.getIdentifiers().get(0);
    assertEquals(createdIdentifier.getId(), updatedIdentifier.getId());
    assertEquals("testDOItitle", updatedIdentifier.getTitle());
    assertEquals("ResearchSpace", updatedIdentifier.getPublisher());
    assertEquals("abcDOI", updatedIdentifier.getDoi());
  }

  @Test
  public void saveSampleWithMultipleSubSamples() {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("mySample");
    newSample.setSubSamples(List.of(new ApiSubSample(), new ApiSubSample()));

    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    assertEquals(2, createdSample.getSubSamples().size());
    assertEquals(2, createdSample.getSubSamplesCount());
    assertEquals("mySample.01", createdSample.getSubSamples().get(0).getName());
    assertEquals("mySample.02", createdSample.getSubSamples().get(1).getName());
  }

  @Test
  public void saveExtraFields() {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("mySample");

    // add two extra numeric fields to sample
    ApiExtraField extraNumeric = new ApiExtraField(ExtraFieldTypeEnum.NUMBER);
    extraNumeric.setName("my numeric");
    extraNumeric.setContent("3.14");
    ApiExtraField extraEmptyNumeric = new ApiExtraField(ExtraFieldTypeEnum.NUMBER);
    extraNumeric.setName("my other numeric");
    extraEmptyNumeric.setContent("");
    newSample.setExtraFields(List.of(extraNumeric, extraEmptyNumeric));

    // add extra string to one of the subsamples
    ApiSubSample subSample1 = new ApiSubSample();
    ApiExtraField extraText = new ApiExtraField();
    extraText.setContent("test content");
    subSample1.setExtraFields(List.of(extraNumeric));
    newSample.setSubSamples(List.of(subSample1, new ApiSubSample()));

    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    assertEquals(2, createdSample.getExtraFields().size());
    assertEquals(extraNumeric.getContent(), createdSample.getExtraFields().get(0).getContent());
    assertEquals(
        extraEmptyNumeric.getContent(), createdSample.getExtraFields().get(1).getContent());

    // check extra field validation error message
    ApiSampleWithFullSubSamples sampleWithInvalidExtraField = new ApiSampleWithFullSubSamples();
    newSample.setName("myInvalidSample");

    ApiExtraField invalidNumeric = new ApiExtraField();
    invalidNumeric.setType(ExtraFieldTypeEnum.NUMBER);
    invalidNumeric.setName("my numeric");
    invalidNumeric.setContent("3.14asdf");
    sampleWithInvalidExtraField.setExtraFields(List.of(invalidNumeric));
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createNewApiSample(sampleWithInvalidExtraField, testUser));
    assertEquals("'3.14asdf' cannot be parsed into number", iae.getMessage());

    // verify extra field cannot be named as default sample field (RSINV-162)
    ApiExtraField unallowedFieldName = new ApiExtraField();
    unallowedFieldName.setType(ExtraFieldTypeEnum.TEXT);
    unallowedFieldName.setName("source");
    unallowedFieldName.setContent("testSource");
    sampleWithInvalidExtraField.setExtraFields(List.of(unallowedFieldName));
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createNewApiSample(sampleWithInvalidExtraField, testUser));
    assertEquals(
        "'source' is not a valid name for a field, as "
            + "there is a default property with this name.",
        iae.getMessage());
  }

  @Test
  public void saveUpdateSampleWithMandatoryFields() {

    ApiSampleTemplate templateWithMandatoryField =
        createSampleTemplateWithMandatoryFields(testUser);

    // sample without default (empty) content in mandatory field
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("mySample");
    newSample.setTemplateId(templateWithMandatoryField.getId());

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createNewApiSample(newSample, testUser));
    assertEquals(
        "[] is invalid for field type Text: Field [myText (mandatory - no default value)] is"
            + " mandatory, but no content is provided",
        iae.getMessage());

    // add content in mandatory fields
    newSample.setFields(Stream.generate(ApiSampleField::new).limit(6).collect(Collectors.toList()));
    newSample.getFields().get(0).setContent("test content 1");
    newSample.getFields().get(1).setContent("test content 2");
    newSample.getFields().get(3).setSelectedOptions(List.of("a"));
    newSample.getFields().get(4).setSelectedOptions(List.of("b"));
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    assertNotNull(createdSample);

    // attempt to update sample with blank content in mandatory text field
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(createdSample.getId());
    sampleUpdates.setName("updated sample");
    ApiSampleField textFieldUpdate = new ApiSampleField();
    textFieldUpdate.setId(createdSample.getFields().get(0).getId());
    textFieldUpdate.setContent(" ");
    sampleUpdates.getFields().add(textFieldUpdate);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSample(sampleUpdates, testUser));
    assertEquals(
        "[ ] is invalid for field type Text: Field [myText (mandatory - with default value)] is"
            + " mandatory, but no content is provided",
        iae.getMessage());

    // attempt to update with blank options in mandatory radio field
    ApiSampleField radioFieldUpdate = new ApiSampleField();
    radioFieldUpdate.setId(createdSample.getFields().get(3).getId());
    radioFieldUpdate.setSelectedOptions(new ArrayList<>());
    sampleUpdates.getFields().clear();
    sampleUpdates.getFields().add(radioFieldUpdate);
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSample(sampleUpdates, testUser));
    assertEquals(
        "[] is invalid for field type Radio: Field [myRadio (mandatory - with default value)] is"
            + " mandatory, but no content is provided",
        iae.getMessage());

    // attempt to update with correct new options
    textFieldUpdate.setContent("updated content");
    radioFieldUpdate.setSelectedOptions(List.of("b"));
    sampleUpdates.getFields().clear();
    sampleUpdates.getFields().addAll(List.of(textFieldUpdate, radioFieldUpdate));
    ApiSample updatedSample = sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    assertNotNull(updatedSample);
    assertEquals("updated content", updatedSample.getFields().get(0).getContent());
    assertEquals("test content 2", updatedSample.getFields().get(1).getContent());
    assertEquals(null, updatedSample.getFields().get(2).getContent());
    assertEquals(List.of("b"), updatedSample.getFields().get(3).getSelectedOptions());
    assertEquals(List.of("b"), updatedSample.getFields().get(4).getSelectedOptions());
    assertEquals(new ArrayList<>(), updatedSample.getFields().get(5).getSelectedOptions());
  }

  @Test
  public void deleteSample() {
    int initialSampleCount = getUserSamples(false).getTotalHits().intValue();
    int initialSampleCountIncludingDeleted = getUserSamples(true).getTotalHits().intValue();

    // create a sample with two subsamples
    ApiSampleWithFullSubSamples newSample = createBasicSampleForUser(testUser);
    assertNotNull(newSample);
    assertFalse(newSample.isDeleted());
    ApiSubSample newSubSample = newSample.getSubSamples().get(0);
    assertFalse(newSubSample.isDeleted());
    subSampleApiMgr.split(SubSampleDuplicateConfig.simpleDuplicate(newSubSample.getId()), testUser);
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    // move first subsample to a container
    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiSubSample updatedSubSample =
        moveSubSampleIntoListContainer(newSubSample.getId(), listContainer.getId(), testUser);
    assertEquals(listContainer.getId(), updatedSubSample.getParentContainer().getId());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryMoveEvent.class));

    int afterCreationSampleCount = getUserSamples(false).getTotalHits().intValue();
    assertEquals(initialSampleCount + 1, afterCreationSampleCount);
    int afterCreationSampleCountIncludingDeleted = getUserSamples(true).getTotalHits().intValue();
    assertEquals(initialSampleCountIncludingDeleted + 1, afterCreationSampleCountIncludingDeleted);

    // try deleting without forceDelete flag
    ApiSample apiSample = sampleApiMgr.markSampleAsDeleted(newSample.getId(), false, testUser);
    assertFalse(apiSample.getCanBeDeleted());
    assertTrue(apiSample.getSubSamples().get(0).isStoredInContainer());
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    Mockito.verify(mockPublisher, Mockito.never())
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    // with forceDelete=true
    sampleApiMgr.markSampleAsDeleted(newSample.getId(), true, testUser);
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    ApiSample deletedSample = sampleApiMgr.getApiSampleById(newSample.getId(), testUser);
    assertTrue(deletedSample.isDeleted());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));
    /* deleted sample lists subsample that were active pre-deletion */
    assertEquals(2, deletedSample.getSubSamples().size());
    assertEquals(2, deletedSample.getSubSamplesCount());
    assertTrue(deletedSample.getSubSamples().get(1).isDeleted());
    assertTrue(deletedSample.getSubSamples().get(1).isDeleted());

    ApiSubSample subSampleOfDeletedSample =
        subSampleApiMgr.getApiSubSampleById(newSubSample.getId(), testUser);
    assertTrue(subSampleOfDeletedSample.isDeleted());
    assertTrue(subSampleOfDeletedSample.isDeletedOnSampleDeletion());
    assertNull(subSampleOfDeletedSample.getParentContainer());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    int afterDeletionSampleCount = getUserSamples(false).getTotalHits().intValue();
    assertEquals(initialSampleCount, afterDeletionSampleCount);

    // can still retrieve sample when querying for deleted samples
    int afterDeletionSampleCountIncludingDeleted = getUserSamples(true).getTotalHits().intValue();
    assertEquals(initialSampleCountIncludingDeleted + 1, afterDeletionSampleCountIncludingDeleted);

    // restore the sample with subsamples
    ApiSample restoredSample = sampleApiMgr.restoreDeletedSample(newSample.getId(), testUser, true);
    assertFalse(restoredSample.isDeleted());
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryRestoreEvent.class));

    // subsample moved to workbench as a part of sample restore
    ApiSubSampleInfo subSampleOfRestoredSample = restoredSample.getSubSamples().get(0);
    assertFalse(subSampleOfRestoredSample.isDeleted());
    assertFalse(subSampleOfRestoredSample.isDeletedOnSampleDeletion());
    assertNotNull(subSampleOfRestoredSample.getParentContainer());
    assertTrue(subSampleOfRestoredSample.getParentContainer().isWorkbench());

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void duplicateSample() throws Exception {
    ApiSampleWithFullSubSamples newSample = createComplexSampleForUser(testUser);
    assertNotNull(newSample.getTemplateId());
    final Long initialSampleFieldCount = getCountOfEntityTable("SampleField");

    ApiContainer workbench = getWorkbenchForUser(testUser);
    int initialWorkbenchContentCount = workbench.getContentSummary().getTotalCount();
    assertEquals(1, initialWorkbenchContentCount); // one subsample

    ApiSampleWithFullSubSamples duplicate = sampleApiMgr.duplicate(newSample.getId(), testUser);
    assertNotNull(duplicate.getTemplateId());
    // all extra fields are created ok
    assertEquals(
        initialSampleFieldCount + duplicate.getFields().size(),
        getCountOfEntityTable("SampleField"));
    assertFalse(duplicate.getId().equals(newSample.getId()));

    // duplicated subsample lives in workbench
    assertEquals(workbench.getId(), duplicate.getSubSamples().get(0).getParentContainer().getId());
    workbench = getWorkbenchForUser(testUser);
    assertEquals(initialWorkbenchContentCount + 1, workbench.getContentSummary().getTotalCount());

    // read permission required to duplicate
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class, () -> sampleApiMgr.duplicate(newSample.getId(), otherUser));
    assertTrue(
        nfe.getMessage().contains("does not exist, or you do not have permission to access it"));

    // edit permission not needed, pi/sysadmin can duplicate fine
    User sysAdminUser = getSysAdminUser();
    duplicate = sampleApiMgr.duplicate(newSample.getId(), sysAdminUser);
    assertEquals(sysAdminUser.getUsername(), duplicate.getOwner().getUsername());
  }

  @Test
  public void groupOwnedSampleVisibilityInsideAndOutsideGroup() {

    // create a pi, with a group
    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // add the group to a new community managed by commAdmin
    User commAdmin = createAndSaveAdminUser();
    Community comm = createAndSaveCommunity(commAdmin, getRandomAlphabeticString("newCommunity"));
    logoutAndLoginAs(commAdmin);
    communityMgr.addGroupToCommunity(group.getId(), comm.getId(), commAdmin);

    // create user outside group
    User otherUser = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(otherUser);

    // create a sample for every user
    ApiSampleWithFullSubSamples testUserSample =
        createBasicSampleForUser(testUser, "testUser's sample");
    ApiSampleWithFullSubSamples piSample = createBasicSampleForUser(pi, "pi's sample");
    ApiSampleWithFullSubSamples otherUserSample =
        createBasicSampleForUser(otherUser, "otherUser's sample");

    /*
     * check visibility for 'testUser' group member
     */

    // testUser see pi's sample in default listing
    ApiSampleSearchResult userSamplesResult =
        sampleApiMgr.getSamplesForUser(null, null, null, testUser);
    assertEquals(2, userSamplesResult.getTotalHits().intValue());
    ApiSampleInfo retrievedTestUserSample = userSamplesResult.getSamples().get(0);
    assertEquals(testUserSample.getName(), retrievedTestUserSample.getName());
    ApiSampleInfo retrievedPiSample = userSamplesResult.getSamples().get(1);
    assertEquals(piSample.getName(), retrievedPiSample.getName());
    // testUser can query full details of pi's sample
    ApiSample fullPiSample = sampleApiMgr.getApiSampleById(retrievedPiSample.getId(), testUser);
    assertFalse(fullPiSample.isClearedForPublicView());
    // testUser have update permission to pi's sample
    assertEquals(2, fullPiSample.getPermittedActions().size());
    // testUser can only see public details of other user's sample
    ApiSample sampleRetrievedByTestUser =
        sampleApiMgr.getApiSampleById(otherUserSample.getId(), testUser);
    assertTrue(sampleRetrievedByTestUser.isClearedForPublicView());

    /*
     * check visibility for 'pi' group member
     */

    // pi see user's sample in default listing
    ApiSampleSearchResult piSamplesResult = sampleApiMgr.getSamplesForUser(null, null, null, pi);
    assertEquals(2, piSamplesResult.getTotalHits().intValue());
    retrievedTestUserSample = piSamplesResult.getSamples().get(0);
    assertEquals(testUserSample.getName(), retrievedTestUserSample.getName());
    retrievedPiSample = piSamplesResult.getSamples().get(1);
    assertEquals(piSample.getName(), retrievedPiSample.getName());
    // pi can query full details of testUsers's sample
    ApiSample fullTestUserSample =
        sampleApiMgr.getApiSampleById(retrievedTestUserSample.getId(), pi);
    assertEquals(testUserSample.getName(), fullTestUserSample.getName());
    // pi have update and transfer permission to user's sample
    assertEquals(3, fullTestUserSample.getPermittedActions().size());
    // pi only see public details of other user's sample
    ApiSample sampleRetrievedByPi = sampleApiMgr.getApiSampleById(otherUserSample.getId(), pi);
    assertTrue(sampleRetrievedByPi.isClearedForPublicView());

    /*
     * check visibility for 'other user' who is outside any group
     */

    // other user only see own sample in default listing
    ApiSampleSearchResult otherUserSamplesResult =
        sampleApiMgr.getSamplesForUser(null, null, null, otherUser);
    assertEquals(1, otherUserSamplesResult.getTotalHits().intValue());
    assertEquals(otherUserSample.getName(), otherUserSamplesResult.getSamples().get(0).getName());
    // will only have public details for other samples
    ApiSample sampleRetrievedByOtherUser =
        sampleApiMgr.getApiSampleById(testUserSample.getId(), otherUser);
    assertTrue(sampleRetrievedByOtherUser.isClearedForPublicView());
    sampleRetrievedByOtherUser = sampleApiMgr.getApiSampleById(piSample.getId(), otherUser);
    assertTrue(sampleRetrievedByOtherUser.isClearedForPublicView());

    /*
     * check visibility for community admin administering the group
     */

    // community admin see all samples in the community
    ApiSampleSearchResult commAdminSamplesResult =
        sampleApiMgr.getSamplesForUser(null, null, null, commAdmin);
    assertEquals(2, commAdminSamplesResult.getTotalHits().intValue());
    // community admin can limit to individual user's sample
    commAdminSamplesResult =
        sampleApiMgr.getSamplesForUser(null, testUser.getUsername(), null, commAdmin);
    assertEquals(1, commAdminSamplesResult.getTotalHits().intValue());
    assertEquals(testUserSample.getName(), commAdminSamplesResult.getSamples().get(0).getName());
    // community admin can query details of a sample within community
    fullTestUserSample = sampleApiMgr.getApiSampleById(testUserSample.getId(), commAdmin);
    assertEquals(testUserSample.getName(), fullTestUserSample.getName());
    // community admin only have read access to sample within community
    assertEquals(1, fullTestUserSample.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordPermittedAction.READ, fullTestUserSample.getPermittedActions().get(0));
    // community admin will only have public details for sample of user outside the community
    ApiSample sampleRetrievedByCommAdmin =
        sampleApiMgr.getApiSampleById(otherUserSample.getId(), commAdmin);
    assertTrue(sampleRetrievedByCommAdmin.isClearedForPublicView());

    /*
     * check visibility for system admin
     */
    User sysadmin = createAndSaveSysadminUser();
    // system admin see all samples in the system
    ApiSampleSearchResult systemAdminSamplesResult =
        sampleApiMgr.getSamplesForUser(null, null, null, sysadmin);
    assertTrue(systemAdminSamplesResult.getTotalHits().intValue() > 2);
    // system admin can limit to individual user's sample
    systemAdminSamplesResult =
        sampleApiMgr.getSamplesForUser(null, testUser.getUsername(), null, sysadmin);
    assertEquals(1, systemAdminSamplesResult.getTotalHits().intValue());
    assertEquals(testUserSample.getName(), systemAdminSamplesResult.getSamples().get(0).getName());
    // system admin can query details of any sample
    fullTestUserSample = sampleApiMgr.getApiSampleById(testUserSample.getId(), sysadmin);
    assertEquals(testUserSample.getName(), fullTestUserSample.getName());
    ApiSample fullOtherUserSample =
        sampleApiMgr.getApiSampleById(otherUserSample.getId(), sysadmin);
    assertEquals(otherUserSample.getName(), fullOtherUserSample.getName());
    // system admin have only read/transfer access to any sample
    assertEquals(2, fullOtherUserSample.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordPermittedAction.READ, fullOtherUserSample.getPermittedActions().get(0));
    assertEquals(
        ApiInventoryRecordPermittedAction.CHANGE_OWNER,
        fullOtherUserSample.getPermittedActions().get(1));
  }

  @Test
  public void whitelistedSampleVisibilityInGroups() {
    // create users and two groups
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user2"));
    User user3 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user3"));
    User user4 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user4"));
    initialiseContentWithEmptyContent(pi1, pi2, user1, user2, user3, user4);

    Group groupA = createGroup("groupA", pi1);
    addUsersToGroup(pi1, groupA, user1, user2);
    Group groupB = createGroup("groupB", pi2);
    addUsersToGroup(pi2, groupB, user3, user4);

    // add lab admins with and without view all permissions to group1
    User labAdminForGroupA = createAndSaveUserIfNotExists("labAdmin");
    User labAdminForGroupAWithViewAll = createAndSaveUserIfNotExists("labAdminWithViewAll1");
    initialiseContentWithEmptyContent(labAdminForGroupA, labAdminForGroupAWithViewAll);
    logoutAndLoginAs(pi1);
    grpMgr.addMembersToGroup(
        groupA.getId(),
        Arrays.asList(new User[] {labAdminForGroupA}),
        "",
        labAdminForGroupA.getUsername(),
        pi1);
    grpMgr.addMembersToGroup(
        groupA.getId(),
        Arrays.asList(new User[] {labAdminForGroupAWithViewAll}),
        "",
        labAdminForGroupAWithViewAll.getUsername(),
        pi1);
    labAdminForGroupAWithViewAll =
        grpMgr.authorizeLabAdminToViewAll(
            labAdminForGroupAWithViewAll.getId(), pi1, groupA.getId(), true);

    // create sample owned by user1, whitelisted just for groupB
    ApiSampleInfo user1Sample =
        createBasicSampleForUser(user1, "user1 sample", "subsample", List.of(groupB));
    assertNotNull(user1Sample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, user1Sample.getSharingMode());
    // create sample owned by user3, group-shared by default
    ApiSampleInfo user3Sample = createBasicSampleForUser(user3, "user3 sample");
    assertNotNull(user3Sample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS, user3Sample.getSharingMode());
    // create sample owned by user4, whitelisted as private
    ApiSampleInfo user4Sample =
        createBasicSampleForUser(user4, "user4 private sample", "subsample", List.of());
    assertNotNull(user4Sample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, user4Sample.getSharingMode());

    // user1 should find own sample fine
    ApiSampleSearchResult visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, user1);
    assertEquals(1, visibleSamples.getTotalHits());
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        3, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/edit/transfer

    // user2 is in groupA, shouldn't see any samples
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, user2);
    assertEquals(0, visibleSamples.getTotalHits());

    // user3 is in groupB, should see own sample and user1's which is whitelisted for groupB
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, user3);
    assertEquals(2, visibleSamples.getTotalHits());
    assertEquals(user3Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        3, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/edit/transfer
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(1).getGlobalId());
    assertEquals(2, visibleSamples.getSamples().get(1).getPermittedActions().size()); // read/update

    // user4 is in groupB, should see own sample, user3's which is group-shared, and user1's which
    // is whitelisted for groupB
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, user4);
    assertEquals(3, visibleSamples.getTotalHits());
    assertEquals(user4Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        3, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/edit/transfer
    assertEquals(user3Sample.getGlobalId(), visibleSamples.getSamples().get(1).getGlobalId());
    assertEquals(2, visibleSamples.getSamples().get(1).getPermittedActions().size()); // read/update
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(2).getGlobalId());
    assertEquals(2, visibleSamples.getSamples().get(2).getPermittedActions().size()); // read/update

    // pi1 should see user1's sample, because they are the user's PI
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, pi1);
    assertEquals(1, visibleSamples.getTotalHits());
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/transfer

    // pi2 should see user3's and user4's container, because they are PI of their group, and user1's
    // container, as it's whitelisted
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, pi2);
    assertEquals(3, visibleSamples.getTotalHits());
    assertEquals(user4Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/transfer
    assertEquals(user3Sample.getGlobalId(), visibleSamples.getSamples().get(1).getGlobalId());
    assertEquals(
        3, visibleSamples.getSamples().get(1).getPermittedActions().size()); // read/edit/transfer
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(2).getGlobalId());
    assertEquals(2, visibleSamples.getSamples().get(2).getPermittedActions().size()); // read/edit

    // labAdmin is treated as a regular member of groupA, shouldn't see any samples
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class), null, null, labAdminForGroupA);
    assertEquals(0, visibleSamples.getTotalHits());

    // labAdminWithViewAll1 should see user1's sample, same as PI of group1
    visibleSamples =
        sampleApiMgr.getSamplesForUser(
            PaginationCriteria.createDefaultForClass(Sample.class),
            null,
            null,
            labAdminForGroupAWithViewAll);
    assertEquals(1, visibleSamples.getTotalHits());
    assertEquals(user1Sample.getGlobalId(), visibleSamples.getSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSamples.getSamples().get(0).getPermittedActions().size()); // read/transfer
  }

  @Test
  public void sampleDuplicationWithinGroup() {
    // create pi with a sample, and a group with test user
    User pi = createAndSaveUserIfNotExists("pi" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    ApiSampleWithFullSubSamples piSample = createBasicSampleForUser(pi, "pi's sample");
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // user can duplicate pi's sample, copied subsample will be placed at user's workbench
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);
    ApiSampleWithFullSubSamples copiedPiSample = sampleApiMgr.duplicate(piSample.getId(), testUser);
    assertEquals("pi's sample_COPY", copiedPiSample.getName());
    assertEquals(testUser.getUsername(), copiedPiSample.getCreatedBy());
    assertEquals(testUser.getUsername(), copiedPiSample.getModifiedBy());
    assertEquals(
        testUser.getUsername(), copiedPiSample.getOwner().getUsername()); // user owns the copy

    ApiSubSample copiedSubSample = copiedPiSample.getSubSamples().get(0);
    assertEquals(
        testUserWorkbench.getGlobalId(), copiedSubSample.getParentContainer().getGlobalId());
    assertEquals(testUser.getUsername(), copiedSubSample.getCreatedBy());
    assertEquals(testUser.getUsername(), copiedSubSample.getModifiedBy());
    assertEquals(testUser.getUsername(), copiedSubSample.getOwner().getUsername());
  }

  @Test
  public void transferSampleToAnotherUser() {
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);

    // create test sample
    ApiSampleWithFullSubSamples testUserSample =
        createBasicSampleForUser(testUser, "testUser's sample");
    assertEquals(testUser.getUsername(), testUserSample.getOwner().getUsername());
    assertEquals(
        testUser.getUsername(), testUserSample.getSubSamples().get(0).getOwner().getUsername());
    assertEquals(
        testUserWorkbench.getId(),
        testUserSample.getSubSamples().get(0).getParentContainer().getId());
    assertEquals(1, testUserSample.getVersion());
    assertEquals(testUser.getUsername(), testUserSample.getModifiedBy());
    Long initialLastModifiedMillis = testUserSample.getLastModifiedMillis();
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    // create another user
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);

    // create change owner request
    ApiSampleInfo sampleUpdate = new ApiSampleInfo();
    sampleUpdate.setId(testUserSample.getId());
    sampleUpdate.setOwner(new ApiUser());

    // check error message for unexisting user
    sampleUpdate.getOwner().setUsername("incorrectUname");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.changeApiSampleOwner(sampleUpdate, testUser));
    assertEquals("Target user [incorrectUname] not found", iae.getMessage());

    // run change owner request as test user
    sampleUpdate.getOwner().setUsername(otherUser.getUsername());
    ApiSample updatedSample = sampleApiMgr.changeApiSampleOwner(sampleUpdate, testUser);
    assertEquals(otherUser.getUsername(), updatedSample.getOwner().getUsername());
    assertTrue(updatedSample.isClearedForPublicView());
    assertNull(updatedSample.getSubSamples()); // no longer has access to the subsamples!
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryTransferEvent.class));

    updatedSample = sampleApiMgr.getApiSampleById(testUserSample.getId(), otherUser);
    assertFalse(updatedSample.isClearedForPublicView());
    assertEquals(
        otherUser.getUsername(), updatedSample.getSubSamples().get(0).getOwner().getUsername());
    assertEquals(
        otherUserWorkbench.getId(),
        updatedSample.getSubSamples().get(0).getParentContainer().getId());

    // version/last modification details not updated (ownership change doesn't count as a
    // modification)
    assertEquals(1, updatedSample.getVersion());
    assertEquals(testUser.getUsername(), updatedSample.getModifiedBy());
    assertEquals(initialLastModifiedMillis, updatedSample.getLastModifiedMillis());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void sampleEditedByTwoUsers() {

    // create a pi, with a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);

    // create a sample
    ApiSampleWithFullSubSamples testSample =
        createBasicSampleForUser(testUser, "testUser's sample");

    // lock sample explicitly by pi
    ApiInventoryEditLock apiLock =
        invLockTracker.attemptToLockForEdit(testSample.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, apiLock.getStatus());

    // try edit by testUser
    testSample.setName("updated name");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSample(testSample, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try delete by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.markSampleAsDeleted(testSample.getId(), false, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try transfer by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.changeApiSampleOwner(testSample, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // pi can edit fine
    ApiSample updatedSample = sampleApiMgr.updateApiSample(testSample, piUser);
    assertEquals("updated name", updatedSample.getName());

    // sample is still locked after update (was not explicitly unlocked)
    assertEquals(
        piUser.getUsername(), invLockTracker.getLockOwnerForItem(testSample.getGlobalId()));

    // pi unlocks
    invLockTracker.attemptToUnlock(testSample.getGlobalId(), piUser);

    // sample is unlocked
    assertNull(invLockTracker.getLockOwnerForItem(testSample.getGlobalId()));

    // testUser can now edit fine, without explicit lock
    testSample.setName("updated name 2");
    updatedSample = sampleApiMgr.updateApiSample(testSample, testUser);
    assertEquals("updated name 2", updatedSample.getName());
  }

  @Test
  public void updatingSampleSharingPermissions() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a sample, check default sharing
    ApiSampleWithFullSubSamples createdSample =
        createBasicSampleForUser(testUser, "testUser's sample");
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS,
        createdSample.getSharingMode());
    assertNotNull(createdSample.getSharedWith());
    assertEquals(1, createdSample.getSharedWith().size());
    assertEquals("groupA", createdSample.getSharedWith().get(0).getGroupInfo().getName());
    assertFalse(createdSample.getSharedWith().get(0).isShared());
    assertTrue(createdSample.getSharedWith().get(0).isItemOwnerGroup());

    // confirm other user can only see public details
    ApiSample sampleRetrievedByOtherUser =
        sampleApiMgr.getApiSampleById(createdSample.getId(), otherUser);
    assertTrue(sampleRetrievedByOtherUser.isClearedForPublicView());

    // save with whitelist permissions pointing to groupB
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(createdSample.getId());
    sampleUpdates.setSharingMode(ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST);
    sampleUpdates.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, testUser)));
    ApiSample updatedSample = sampleApiMgr.updateApiSample(sampleUpdates, testUser);
    assertNotNull(updatedSample);
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, updatedSample.getSharingMode());
    assertNotNull(updatedSample.getSharedWith());
    assertEquals(2, updatedSample.getSharedWith().size());
    assertEquals("groupA", updatedSample.getSharedWith().get(0).getGroupInfo().getName());
    assertFalse(
        updatedSample.getSharedWith().get(0).isShared()); // owner's groups are always present
    assertTrue(updatedSample.getSharedWith().get(0).isItemOwnerGroup());
    assertEquals("groupB", updatedSample.getSharedWith().get(1).getGroupInfo().getName());
    assertTrue(updatedSample.getSharedWith().get(1).isShared());
    assertFalse(updatedSample.getSharedWith().get(1).isItemOwnerGroup());

    // confirm other user can retrieve and has access to read/edit actions
    sampleRetrievedByOtherUser = sampleApiMgr.getApiSampleById(createdSample.getId(), otherUser);
    assertFalse(sampleRetrievedByOtherUser.isClearedForPublicView());
    assertEquals(2, sampleRetrievedByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordPermittedAction.READ,
        sampleRetrievedByOtherUser.getPermittedActions().get(0));
    assertEquals(
        ApiInventoryRecordPermittedAction.UPDATE,
        sampleRetrievedByOtherUser.getPermittedActions().get(1));
  }

  @Test
  public void checkLimitedReadSampleActions() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a container shared with groupB, and a sample with subsample in that container
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", List.of(groupB));
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(testUser);
    moveSubSampleIntoListContainer(
        createdSample.getSubSamples().get(0).getId(), apiContainer.getId(), testUser);

    // as user2 try retrieving sample
    ApiSample sampleAsSeenByOtherUser =
        sampleApiMgr.getApiSampleById(createdSample.getId(), otherUser);
    assertEquals(1, sampleAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordPermittedAction.LIMITED_READ,
        sampleAsSeenByOtherUser.getPermittedActions().get(0));
    // assert only some fields populated
    assertNotNull(sampleAsSeenByOtherUser.getName());
    assertNotNull(sampleAsSeenByOtherUser.getBarcodes());
    assertNotNull(sampleAsSeenByOtherUser.getTags());
    assertNull(sampleAsSeenByOtherUser.getFields());
    assertNull(sampleAsSeenByOtherUser.getExtraFields());
    assertNull(sampleAsSeenByOtherUser.getSubSamples());

    // compare with sample as seen by the owner
    ApiSample sampleAsSeenByTestUser =
        sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals(3, sampleAsSeenByTestUser.getPermittedActions().size());
    assertNotNull(sampleAsSeenByTestUser.getFields());
    assertNotNull(sampleAsSeenByTestUser.getExtraFields());
    assertNotNull(sampleAsSeenByTestUser.getSubSamples());
  }
}
