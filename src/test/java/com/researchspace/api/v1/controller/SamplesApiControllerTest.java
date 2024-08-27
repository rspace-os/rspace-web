package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.SamplesApiControllerMVCIT.NUM_FIELDS_IN_COMPLEX_SAMPLE;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples.ApiSampleSubSampleTargetLocation;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SamplesApiControllerTest extends SpringTransactionalTest {

  private @Autowired SamplesApiController samplesApi;
  @Mock private DocumentTagManagerImpl documentTagManagerMock;
  @Autowired private SampleApiManager sampleApiMgr;
  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  @Before
  public void setUp() {
    openMocks(this);
    sampleDao.resetDefaultTemplateOwner();
    ReflectionTestUtils.setField(sampleApiMgr, "documentTagManager", documentTagManagerMock);
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  /** self, icon, thumbnail, image */
  final int EXPECTED_SAMPLE_LINKS_COUNT = 4;

  @Test
  public void createSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    newSample = createSampleWithSubSamples("");
    // set tags - create will write to ontology doc
    newSample.setApiTagInfo("Some tags");
    samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void changeSampleOwnerShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    samplesApi.changeSampleOwner(newSample.getId(), newSample, mockBindingResult, piUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(piUser));
    // set tags - change owner will write to ontology doc
    newSample.setApiTagInfo("Some tags");
    samplesApi.updateSample(newSample.getId(), newSample, mockBindingResult, piUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(piUser));
    samplesApi.changeSampleOwner(newSample.getId(), newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void restoreSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    samplesApi.deleteSample(newSample.getId(), true, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    samplesApi.restoreDeletedSample(newSample.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - restore will write to ontology doc
    newSample.setApiTagInfo("Some tags");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    samplesApi.deleteSample(newSample.getId(), true, testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
    samplesApi.restoreDeletedSample(newSample.getId(), testUser);
    verify(documentTagManagerMock, times(3)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void updateSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    // change name - no writing to use ontology doc
    newSample.setName("updated name");
    samplesApi.updateSample(newSample.getId(), newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // change tags - update will write to ontology doc
    newSample.setApiTagInfo("some tags");
    samplesApi.updateSample(newSample.getId(), newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void deleteSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    samplesApi.deleteSample(newSample.getId(), true, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - delete will write to ontology doc
    newSample.setApiTagInfo("Some tags");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    samplesApi.deleteSample(newSample.getId(), true, testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void retrievePaginatedSampleList() throws BindException {
    List<ApiInventoryRecordInfo> createdSampleList =
        createMultipleSamplesForUser("sample XYZ", 21, testUser);
    assertEquals(21, createdSampleList.size());

    // no pagination parameters
    ApiSampleSearchResult defaultSamples =
        samplesApi.getSamplesForUser(null, null, mockBindingResult, testUser);
    assertEquals(21, defaultSamples.getTotalHits().intValue());
    assertEquals(20, defaultSamples.getSamples().size());
    assertEquals(2, defaultSamples.getLinks().size());
    ApiSampleInfo firstSampleInfo = defaultSamples.getSamples().get(0);
    assertEquals("sample XYZ-01", firstSampleInfo.getName());
    assertEquals(testUser.getFullName(), firstSampleInfo.getModifiedByFullName());
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, firstSampleInfo.getLinks().size());

    // third page, default ordering
    InventoryApiPaginationCriteria apiPgCrit = new InventoryApiPaginationCriteria(2, 5, null);
    ApiSampleSearchResult paginatedSamples =
        samplesApi.getSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(21, paginatedSamples.getTotalHits().intValue());
    assertEquals(5, paginatedSamples.getSamples().size());
    assertEquals("sample XYZ-11", paginatedSamples.getSamples().get(0).getName());
    assertEquals(5, paginatedSamples.getLinks().size());

    // third page, reverse ordering
    apiPgCrit = new InventoryApiPaginationCriteria(2, 5, "name desc");
    ApiSampleSearchResult paginatedSamplesDesc =
        samplesApi.getSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(21, paginatedSamplesDesc.getTotalHits().intValue());
    assertEquals(5, paginatedSamplesDesc.getSamples().size());
    assertEquals("sample XYZ-11", paginatedSamplesDesc.getSamples().get(0).getName());
    assertEquals(5, paginatedSamplesDesc.getLinks().size());
  }

  private ApiSampleWithFullSubSamples createSampleWithSubSamples(String tags) {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("sample XYZ");
    newSample.setApiTagInfo(tags);
    newSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.CELSIUS));

    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setName("subSample WXY");
    newSubSample.getNotes().add(new ApiSubSampleNote("subsample notes 1"));
    newSubSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.MILLI_LITRE));
    ApiSubSample newSubSample2 = new ApiSubSample();
    newSubSample2.setName("subSample WXY #2");
    newSubSample2.getNotes().add(new ApiSubSampleNote("subsample notes 2"));
    newSubSample2.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(25), RSUnitDef.MILLI_LITRE));
    newSample.setSubSamples(List.of(newSubSample, newSubSample2));
    return newSample;
  }

  @Test
  public void createRetrieveSampleWithSubSamples() throws Exception {
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("one, two");
    ApiSubSample newSubSample = newSample.getSubSamples().get(0);
    ApiSubSample newSubSample2 = newSample.getSubSamples().get(1);
    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(createdSample);
    // sample details
    assertNotNull(createdSample.getId());
    assertNotNull(createdSample.getOwner());
    assertEquals(testUser.getUsername(), createdSample.getOwner().getUsername());
    assertEquals(newSample.getName(), createdSample.getName());
    assertEquals(newSample.getTags(), createdSample.getTags());
    assertEquals(newSample.getStorageTempMin(), createdSample.getStorageTempMin());
    assertEquals(newSample.getStorageTempMax(), createdSample.getStorageTempMax());
    assertEquals("30 ml", createdSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        SubSampleName.ALIQUOT.getDisplayName(), createdSample.getSubSampleAlias().getAlias());
    // fields/links
    assertEquals(0, createdSample.getFields().size());
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, createdSample.getLinks().size());
    ApiLinkItem sampleLink = createdSample.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertTrue(sampleLink.getLink().endsWith("/api/inventory/v1/samples/" + createdSample.getId()));
    // subsamples
    assertEquals(2, createdSample.getSubSamples().size());
    assertEquals(2, createdSample.getSubSamplesCount());
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    assertNotNull(createdSubSample.getId());
    assertEquals(newSubSample.getName(), createdSubSample.getName());
    assertEquals(newSubSample.getQuantity(), createdSubSample.getQuantity());
    ApiSubSampleInfo createdSubSample2 = createdSample.getSubSamples().get(1);
    assertNotNull(createdSubSample2.getId());
    assertEquals(newSubSample2.getName(), createdSubSample2.getName());
    assertEquals(newSubSample2.getQuantity(), createdSubSample2.getQuantity());
  }

  @Test
  public void createRetrieveComplexSample() throws Exception {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("complex sample XYZ");
    newSample.setApiTagInfo("api, test");
    newSample.setNewBase64Image(getBase64Image());
    newSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(-30), RSUnitDef.CELSIUS));
    newSample.setStorageTempMax(new ApiQuantityInfo(BigDecimal.valueOf(-20), RSUnitDef.CELSIUS));
    newSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(10), RSUnitDef.GRAM));

    ApiExtraField extraApiNumberField = new ApiExtraField();
    extraApiNumberField.setType(ExtraFieldTypeEnum.NUMBER);
    newSample.setExtraFields(List.of(extraApiNumberField));

    Sample sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    // add default value to various fields
    sampleTemplate.getActiveFields().get(4).setData("text"); // text
    sampleTemplate.getActiveFields().get(8).setData("option1"); // radio
    sampleTemplate.getActiveFields().get(9).setSelectedOptions(List.of("optionA")); // choice
    Sample savedTemplate = sampleDao.persistSampleTemplate(sampleTemplate);
    newSample.setTemplateId(savedTemplate.getId());

    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(createdSample);

    // sample properties
    assertNotNull(createdSample.getId());
    assertNotNull(createdSample.getOwner());
    assertEquals(testUser.getUsername(), createdSample.getOwner().getUsername());
    assertEquals(newSample.getName(), createdSample.getName());
    assertEquals(newSample.getTags(), createdSample.getTags());
    assertEquals(
        newSample.getStorageTempMin().toQuantityInfo(),
        createdSample.getStorageTempMin().toQuantityInfo());
    assertEquals(
        newSample.getStorageTempMax().toQuantityInfo(),
        createdSample.getStorageTempMax().toQuantityInfo());
    assertEquals(
        newSample.getQuantity().toQuantityInfo(), createdSample.getQuantity().toQuantityInfo());
    assertEquals(
        SubSampleName.ALIQUOT.getDisplayName(), createdSample.getSubSampleAlias().getAlias());
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, createdSample.getLinks().size());

    // check default subsample
    assertEquals(1, createdSample.getSubSamples().size());
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    assertNotNull(createdSubSample.getId());
    assertEquals("complex sample XYZ.01", createdSubSample.getName());
    assertEquals(
        newSample.getQuantity().toQuantityInfo(), createdSubSample.getQuantity().toQuantityInfo());
    assertEquals(3, createdSubSample.getLinks().size()); // self + image links

    // check fields and default values assignment
    assertEquals(NUM_FIELDS_IN_COMPLEX_SAMPLE, createdSample.getFields().size());
    assertEquals("text", createdSample.getFields().get(4).getContent());
    assertEquals(List.of("option1"), createdSample.getFields().get(8).getSelectedOptions());
    assertEquals(List.of("optionA"), createdSample.getFields().get(9).getSelectedOptions());
    assertEquals(1, createdSample.getExtraFields().size());

    // retrieve
    ApiSample retrievedSample = samplesApi.getSampleById(createdSample.getId(), testUser);
    assertEquals(testUser.getFullName(), retrievedSample.getModifiedByFullName());
    assertNotNull(retrievedSample.getOwner());
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, retrievedSample.getLinks().size());
    assertEquals(1, retrievedSample.getSubSamples().size());
    ApiSubSampleInfo retrievedSubSample = retrievedSample.getSubSamples().get(0);
    assertEquals(testUser.getFullName(), retrievedSubSample.getModifiedByFullName());
    assertNotNull(retrievedSubSample.getOwner());
    assertEquals(3, retrievedSubSample.getLinks().size());
  }

  @Test
  public void createComplexSampleWithProvidedFieldContent() throws Exception {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("complex sample with field content");

    Sample sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    Sample savedTemplate = sampleDao.persistSampleTemplate(sampleTemplate);
    newSample.setTemplateId(savedTemplate.getId());

    List<ApiSampleField> fields = new ArrayList<>();
    ApiSampleField numberField = new ApiSampleField();
    numberField.setContent("3.14");
    fields.add(numberField);
    ApiSampleField dateField = new ApiSampleField();
    fields.add(dateField);
    ApiSampleField stringField = new ApiSampleField();
    fields.add(stringField);
    ApiSampleField textField = new ApiSampleField();
    fields.add(textField);
    ApiSampleField urlField = new ApiSampleField();
    fields.add(urlField);
    ApiSampleField refrenceField = new ApiSampleField();
    fields.add(refrenceField);
    ApiSampleField attachmentField = new ApiSampleField();
    fields.add(attachmentField);
    ApiSampleField timeField = new ApiSampleField();
    fields.add(timeField);
    ApiSampleField radioField = new ApiSampleField();
    radioField.setSelectedOptions(List.of("option1"));
    fields.add(radioField);
    ApiSampleField choiceField = new ApiSampleField();
    choiceField.setSelectedOptions(List.of("optionA", "optionB"));
    fields.add(choiceField);
    newSample.setFields(fields);

    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(createdSample);

    // sample properties
    assertNotNull(createdSample.getId());
    assertNotNull(createdSample.getOwner());
    assertEquals(testUser.getUsername(), createdSample.getOwner().getUsername());
    assertEquals(newSample.getName(), createdSample.getName());

    // check fields and values assignment
    assertEquals(NUM_FIELDS_IN_COMPLEX_SAMPLE, createdSample.getFields().size());
    assertEquals("3.14", createdSample.getFields().get(0).getContent());
    assertEquals(
        null,
        createdSample
            .getFields()
            .get(1)
            .getContent()); // defaults get overridden by content in request
    assertEquals(List.of("option1"), createdSample.getFields().get(8).getSelectedOptions());
    assertEquals(
        List.of("optionA", "optionB"), createdSample.getFields().get(9).getSelectedOptions());
  }

  @Test
  public void createSampleWithMultipleSubSamples() throws Exception {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("sample XYZ");
    newSample.setNewSampleSubSamplesCount(3);
    newSample.setApiTagInfo("api, test");
    newSample.setStorageTempMin(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.CELSIUS));
    ApiQuantityInfo quantity10ML =
        new ApiQuantityInfo(BigDecimal.valueOf(10), RSUnitDef.MILLI_LITRE);
    newSample.setQuantity(quantity10ML);
    ApiQuantityInfo quantity9dot999ML =
        new ApiQuantityInfo(BigDecimal.valueOf(9.999), RSUnitDef.MILLI_LITRE);

    // 3 subsamples
    ApiSampleWithFullSubSamples sampleWithSubSamples =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(sampleWithSubSamples);
    assertNotNull(sampleWithSubSamples.getId());
    assertEquals("sample XYZ", sampleWithSubSamples.getName());
    assertEquals(newSample.getTags(), sampleWithSubSamples.getTags());
    assertEquals(
        quantity9dot999ML,
        sampleWithSubSamples
            .getQuantity()); // expected - that's actual sum of rounded individual quantities
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, sampleWithSubSamples.getLinks().size());
    ApiLinkItem sampleLink = sampleWithSubSamples.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertTrue(
        sampleLink.getLink().endsWith("/api/inventory/v1/samples/" + sampleWithSubSamples.getId()));
    // subsample serie
    assertEquals(3, sampleWithSubSamples.getSubSamples().size());
    assertEquals("sample XYZ.01", sampleWithSubSamples.getSubSamples().get(0).getName());
    assertEquals("sample XYZ.03", sampleWithSubSamples.getSubSamples().get(2).getName());
    // total sample quantity split when calculating subsamples
    ApiQuantityInfo quantity3and3rdML =
        new ApiQuantityInfo(BigDecimal.valueOf(3.333), RSUnitDef.MILLI_LITRE);
    assertEquals(quantity3and3rdML, sampleWithSubSamples.getSubSamples().get(0).getQuantity());
    assertEquals(quantity3and3rdML, sampleWithSubSamples.getSubSamples().get(2).getQuantity());

    // delete sample
    assertFalse(sampleWithSubSamples.isDeleted());
    assertNull(sampleWithSubSamples.getDeletedDate());

    samplesApi.deleteSample(sampleWithSubSamples.getId(), false, testUser);
    ApiSample deletedSample = samplesApi.getSampleById(sampleWithSubSamples.getId(), testUser);
    assertTrue(deletedSample.isDeleted());
    assertNotNull(deletedSample.getDeletedDate());
    // subsamples still active
    assertEquals(3, deletedSample.getSubSamples().size());
  }

  @Test
  public void createSampleWithDefaultQuantity() throws Exception {
    // sample with single subsample
    ApiSampleWithFullSubSamples newSample =
        new ApiSampleWithFullSubSamples("sample with default quantity");
    ApiSampleWithFullSubSamples sampleWithDefaultQuantity =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(sampleWithDefaultQuantity);
    assertEquals(1, sampleWithDefaultQuantity.getSubSamplesCount());
    assertEquals("1 ml", sampleWithDefaultQuantity.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "1 ml",
        sampleWithDefaultQuantity
            .getSubSamples()
            .get(0)
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());

    // sample with 3 subsamples
    newSample = new ApiSampleWithFullSubSamples("sample with 3 subsamples and default quantity");
    newSample.setNewSampleSubSamplesCount(3);
    ApiSampleWithFullSubSamples sampleWithSubSamples =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(sampleWithSubSamples);
    assertEquals(3, sampleWithSubSamples.getSubSamplesCount());
    assertEquals("3 ml", sampleWithSubSamples.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "1 ml",
        sampleWithSubSamples.getSubSamples().get(0).getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void createSampleWithEmptyBarcode_rsinv847() throws Exception {
    // sample with single subsample
    ApiSampleWithFullSubSamples newSample =
        new ApiSampleWithFullSubSamples("sample with empty barcode quantity");
    // barcode without any data, just newBarcodeRequest flag
    ApiBarcode barcodeRequest = new ApiBarcode();
    barcodeRequest.setNewBarcodeRequest(true);
    newSample.setBarcodes(List.of(barcodeRequest));

    ApiSampleWithFullSubSamples sampleWithEmptyBarcode =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(sampleWithEmptyBarcode); // no error
    assertEquals(1, sampleWithEmptyBarcode.getBarcodes().size());
    ApiBarcode createdBarcode = sampleWithEmptyBarcode.getBarcodes().get(0);
    assertNull(createdBarcode.getData());
    assertEquals(testUser.getUsername(), createdBarcode.getCreatedBy());
  }

  @Test
  public void createSampleWithDefaultSubSamplesInVariousLocations() throws Exception {
    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 2, 2);
    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);

    // sample with 3 subsamples, each in a different container
    ApiSampleWithFullSubSamples newSample =
        new ApiSampleWithFullSubSamples("sample with 3 subsamples and default quantity");
    newSample.setNewSampleSubSamplesCount(3);
    List<ApiSampleSubSampleTargetLocation> targetLocations =
        List.of(
            new ApiSampleSubSampleTargetLocation(listContainer.getId(), new ApiContainerLocation()),
            new ApiSampleSubSampleTargetLocation(
                gridContainer.getId(), new ApiContainerLocation(1, 2)),
            new ApiSampleSubSampleTargetLocation(
                imageContainer.getId(), imageContainer.getLocations().get(1)));
    newSample.setNewSampleSubSampleTargetLocations(targetLocations);

    ApiSampleWithFullSubSamples sampleWithSubSamples =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(sampleWithSubSamples);
    assertEquals(3, sampleWithSubSamples.getSubSamplesCount());
    assertEquals(
        "listContainer",
        sampleWithSubSamples.getSubSamples().get(0).getParentContainer().getName());
    assertEquals(
        "gridContainer",
        sampleWithSubSamples.getSubSamples().get(1).getParentContainer().getName());
    assertEquals(
        "imageContainer",
        sampleWithSubSamples.getSubSamples().get(2).getParentContainer().getName());
  }

  @Test
  public void retrieveDefaultDevRunProfileSamples() throws BindException {
    User exampleContentUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(exampleContentUser);
    logoutAndLoginAs(exampleContentUser);

    // check default samples
    ApiSampleSearchResult userSamples =
        samplesApi.getSamplesForUser(null, null, mockBindingResult, exampleContentUser);
    assertEquals(2, userSamples.getTotalHits().intValue());
    ApiSampleInfo basicSampleInfo = userSamples.getSamples().get(0);
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, basicSampleInfo.getLinks().size());
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_BASIC_SAMPLE_NAME, basicSampleInfo.getName());
    ApiLinkItem sampleLink = basicSampleInfo.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertTrue(
        sampleLink.getLink().endsWith("/api/inventory/v1/samples/" + basicSampleInfo.getId()));

    // get full details of complex sample
    ApiSample complexSample =
        samplesApi.getSampleById(userSamples.getSamples().get(1).getId(), exampleContentUser);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_COMPLEX_SAMPLE_NAME, complexSample.getName());
    assertEquals(0, complexSample.getAttachments().size());

    // check attachment field of complex sample has a file attached to it
    ApiSampleField attachmentField = complexSample.getFields().get(6);
    assertEquals("MyAttachment", attachmentField.getName());
    assertNotNull(attachmentField.getAttachment());
    assertEquals("loremIpsem20para.txt", attachmentField.getAttachment().getName());
    assertEquals(10295L, attachmentField.getAttachment().getSize());
    assertEquals(2, attachmentField.getAttachment().getLinks().size());
  }

  @Test
  public void updateDefaultBasicSample() throws Exception {
    User userWithContent = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(userWithContent);
    logoutAndLoginAs(userWithContent);

    ApiSampleSearchResult userSamples =
        samplesApi.getSamplesForUser(null, null, mockBindingResult, userWithContent);
    assertEquals(2, userSamples.getTotalHits().intValue());
    ApiSampleInfo complexSampleInfo = userSamples.getSamples().get(1);
    ApiSample complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertEquals(1, complexSample.getExtraFields().size());

    // change sample name, add new extra field
    ApiSampleWithFullSubSamples sampleUpdate = new ApiSampleWithFullSubSamples();
    sampleUpdate.setName("updated name");
    ApiExtraField extraFieldAddition = new ApiExtraField();
    extraFieldAddition.setNewFieldRequest(true);
    extraFieldAddition.setContent("new field content");
    sampleUpdate.getExtraFields().add(extraFieldAddition);

    ApiSampleWithoutSubSamples updatedSample =
        samplesApi.updateSample(
            complexSampleInfo.getId(), sampleUpdate, mockBindingResult, userWithContent);
    assertEquals(EXPECTED_SAMPLE_LINKS_COUNT, updatedSample.getLinks().size());
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertEquals(complexSample, updatedSample);
    assertEquals("updated name", complexSample.getName());
    assertEquals(2, complexSample.getExtraFields().size());

    ApiExtraField newlyAddedExtraField = complexSample.getExtraFields().get(1);
    assertNotNull(newlyAddedExtraField.getId());
    assertEquals("Data", newlyAddedExtraField.getName());
    assertEquals("new field content", newlyAddedExtraField.getContent());

    // create sample changeset that modifies newly added extra field & deletes original extra field
    ApiExtraField extraFieldModification = new ApiExtraField();
    extraFieldModification.setId(newlyAddedExtraField.getId());
    extraFieldModification.setName("updated field name");
    extraFieldModification.setContent("updated field content");
    sampleUpdate.getExtraFields().add(extraFieldModification);
    ApiExtraField extraFieldDeletion = new ApiExtraField();
    extraFieldDeletion.setId(complexSample.getExtraFields().get(0).getId());
    extraFieldDeletion.setDeleteFieldRequest(true);
    sampleUpdate = new ApiSampleWithFullSubSamples();
    sampleUpdate.getExtraFields().add(extraFieldModification);
    sampleUpdate.getExtraFields().add(extraFieldDeletion);

    // apply changeset
    updatedSample =
        samplesApi.updateSample(
            complexSampleInfo.getId(), sampleUpdate, mockBindingResult, userWithContent);
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertEquals(complexSample, updatedSample);
    assertEquals(1, complexSample.getExtraFields().size());
    assertEquals("updated field name", complexSample.getExtraFields().get(0).getName());
    assertEquals("updated field content", complexSample.getExtraFields().get(0).getContent());

    // delete sample
    samplesApi.deleteSample(complexSampleInfo.getId(), false, userWithContent);
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertTrue(complexSample.isDeleted());
  }

  @Test
  public void sampleTemplateActionsRequestsUsingTemplatesEndpoint() throws BindException {

    // try creating simple template through samples controller
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("sample template XYZ");
    newSample.setApiTagInfo("api, test");
    newSample.setTemplate(true);

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> samplesApi.createNewSample(newSample, mockBindingResult, testUser));
    assertEquals("Please use /sampleTemplates endpoint for template actions", iae.getMessage());

    Sample sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    Sample savedTemplate = sampleDao.persistSampleTemplate(sampleTemplate);
    newSample.setTemplateId(savedTemplate.getId());

    // try changing template name through samples controller
    ApiSampleWithFullSubSamples sampleUpdate = new ApiSampleWithFullSubSamples();
    sampleUpdate.setName("updated name");
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                samplesApi.updateSample(
                    savedTemplate.getId(), sampleUpdate, mockBindingResult, testUser));
    assertEquals("Please use /sampleTemplates endpoint for template actions", iae.getMessage());
  }

  @Test
  public void checkRevisionHistoryMethods() throws Exception {
    // revisions are only created in real database transaction, this test just runs the code

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiInventoryRecordRevisionList revisions =
        samplesApi.getSampleAllRevisions(basicSample.getId(), testUser);
    assertEquals(0, revisions.getRevisions().size());

    ApiSample sample = samplesApi.getSampleRevision(basicSample.getId(), 1L, testUser);
    assertNull(sample);
  }
}
