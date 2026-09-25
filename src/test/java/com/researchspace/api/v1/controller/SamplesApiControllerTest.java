package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.SamplesApiControllerMVCIT.NUM_FIELDS_IN_COMPLEX_SAMPLE;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.dao.SampleTemplateDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.ws.rs.NotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SamplesApiControllerTest extends SpringTransactionalTest {

  private @Autowired SamplesApiController samplesApi;
  private @Autowired SampleTemplateDao sampleTemplateDao;
  @Mock private DocumentTagManagerImpl documentTagManagerMock;
  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  private static final String PIC1_MAIN_IMAGE_CONTENT_HASH =
      "6624312783e502f98c018690891dde44e1387f2781256457e9fe56185a7c7fcc";
  private static final String PIC1_THUMBNAIL_CONTENT_HASH =
      "5ea6a96ab0150456ef34ca30e086c991696f8f59f18439a9e8cd5e565189f13b";

  @BeforeEach
  public void setUp() {
    openMocks(this);
    sampleTemplateDao.resetDefaultTemplateOwner();
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
    assertThat(createdSampleList).hasSize(21);

    // no pagination parameters
    ApiSampleSearchResult defaultSamples =
        samplesApi.getSamplesForUser(null, null, mockBindingResult, testUser);
    assertEquals(21, defaultSamples.getTotalHits().intValue());
    assertThat(defaultSamples.getSamples()).hasSize(20);
    assertThat(defaultSamples.getLinks()).hasSize(2);
    ApiSampleInfo firstSampleInfo = defaultSamples.getSamples().get(0);
    assertEquals("sample XYZ-01", firstSampleInfo.getName());
    assertEquals(testUser.getFullName(), firstSampleInfo.getModifiedByFullName());
    assertThat(firstSampleInfo.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);

    // third page, default ordering
    InventoryApiPaginationCriteria apiPgCrit = new InventoryApiPaginationCriteria(2, 5, null);
    ApiSampleSearchResult paginatedSamples =
        samplesApi.getSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(21, paginatedSamples.getTotalHits().intValue());
    assertThat(paginatedSamples.getSamples()).hasSize(5);
    assertEquals("sample XYZ-11", paginatedSamples.getSamples().get(0).getName());
    assertThat(paginatedSamples.getLinks()).hasSize(5);

    // third page, reverse ordering
    apiPgCrit = new InventoryApiPaginationCriteria(2, 5, "name desc");
    ApiSampleSearchResult paginatedSamplesDesc =
        samplesApi.getSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(21, paginatedSamplesDesc.getTotalHits().intValue());
    assertThat(paginatedSamplesDesc.getSamples()).hasSize(5);
    assertEquals("sample XYZ-11", paginatedSamplesDesc.getSamples().get(0).getName());
    assertThat(paginatedSamplesDesc.getLinks()).hasSize(5);
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
    assertThat(createdSample.getFields()).isEmpty();
    assertThat(createdSample.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);
    ApiLinkItem sampleLink = createdSample.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertThat(sampleLink.getLink()).endsWith("/api/inventory/v1/samples/" + createdSample.getId());
    // subsamples
    assertThat(createdSample.getSubSamples()).hasSize(2);
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

    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    // add default value to various fields
    sampleTemplate.getActiveFields().get(4).setData("text"); // text
    sampleTemplate.getActiveFields().get(8).setData("option1"); // radio
    sampleTemplate.getActiveFields().get(9).setSelectedOptions(List.of("optionA")); // choice
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
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
    assertThat(createdSample.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);

    // check default subsample
    assertThat(createdSample.getSubSamples()).hasSize(1);
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    assertNotNull(createdSubSample.getId());
    assertEquals("complex sample XYZ.01", createdSubSample.getName());
    assertEquals(
        newSample.getQuantity().toQuantityInfo(), createdSubSample.getQuantity().toQuantityInfo());
    assertThat(createdSubSample.getLinks()).hasSize(3); // self + image links

    // check fields and default values assignment
    assertThat(createdSample.getFields()).hasSize(NUM_FIELDS_IN_COMPLEX_SAMPLE);
    assertEquals("text", createdSample.getFields().get(4).getContent());
    assertThat(createdSample.getFields().get(8).getSelectedOptions()).containsExactly("option1");
    assertThat(createdSample.getFields().get(9).getSelectedOptions()).containsExactly("optionA");
    assertThat(createdSample.getExtraFields()).hasSize(1);

    // retrieve
    ApiSample retrievedSample = samplesApi.getSampleById(createdSample.getId(), testUser);
    assertEquals(testUser.getFullName(), retrievedSample.getModifiedByFullName());
    assertNotNull(retrievedSample.getOwner());
    assertThat(retrievedSample.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);
    assertThat(retrievedSample.getSubSamples()).hasSize(1);
    ApiSubSampleInfo retrievedSubSample = retrievedSample.getSubSamples().get(0);
    assertEquals(testUser.getFullName(), retrievedSubSample.getModifiedByFullName());
    assertNotNull(retrievedSubSample.getOwner());
    assertThat(retrievedSubSample.getLinks()).hasSize(3);
  }

  @Test
  public void createComplexSampleWithProvidedFieldContent() throws Exception {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("complex sample with field content");

    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    newSample.setTemplateId(savedTemplate.getId());

    List<ApiInventoryEntityField> fields = new ArrayList<>();
    ApiInventoryEntityField numberField = new ApiInventoryEntityField();
    numberField.setContent("3.14");
    fields.add(numberField);
    ApiInventoryEntityField dateField = new ApiInventoryEntityField();
    fields.add(dateField);
    ApiInventoryEntityField stringField = new ApiInventoryEntityField();
    fields.add(stringField);
    ApiInventoryEntityField textField = new ApiInventoryEntityField();
    fields.add(textField);
    ApiInventoryEntityField urlField = new ApiInventoryEntityField();
    fields.add(urlField);
    ApiInventoryEntityField refrenceField = new ApiInventoryEntityField();
    fields.add(refrenceField);
    ApiInventoryEntityField attachmentField = new ApiInventoryEntityField();
    fields.add(attachmentField);
    ApiInventoryEntityField timeField = new ApiInventoryEntityField();
    fields.add(timeField);
    ApiInventoryEntityField radioField = new ApiInventoryEntityField();
    radioField.setSelectedOptions(List.of("option1"));
    fields.add(radioField);
    ApiInventoryEntityField choiceField = new ApiInventoryEntityField();
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
    assertThat(createdSample.getFields()).hasSize(NUM_FIELDS_IN_COMPLEX_SAMPLE);
    assertEquals("3.14", createdSample.getFields().get(0).getContent());
    assertNull(
        createdSample
            .getFields()
            .get(1)
            .getContent()); // defaults get overridden by content in request
    assertThat(createdSample.getFields().get(8).getSelectedOptions()).containsExactly("option1");
    assertThat(createdSample.getFields().get(9).getSelectedOptions())
        .containsExactly("optionA", "optionB");
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
    assertThat(sampleWithSubSamples.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);
    ApiLinkItem sampleLink = sampleWithSubSamples.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertThat(sampleLink.getLink())
        .endsWith("/api/inventory/v1/samples/" + sampleWithSubSamples.getId());
    // subsample serie
    assertThat(sampleWithSubSamples.getSubSamples()).hasSize(3);
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
    assertThat(deletedSample.getSubSamples()).hasSize(3);
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
    assertThat(sampleWithEmptyBarcode.getBarcodes()).hasSize(1);
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
    List<ApiTargetLocation> targetLocations =
        List.of(
            new ApiTargetLocation(listContainer.getId(), new ApiContainerLocation()),
            new ApiTargetLocation(gridContainer.getId(), new ApiContainerLocation(1, 2)),
            new ApiTargetLocation(imageContainer.getId(), imageContainer.getLocations().get(1)));
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
    assertThat(basicSampleInfo.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_BASIC_SAMPLE_NAME, basicSampleInfo.getName());
    ApiLinkItem sampleLink = basicSampleInfo.getLinks().get(0);
    assertEquals(ApiLinkItem.SELF_REL, sampleLink.getRel());
    assertThat(sampleLink.getLink())
        .endsWith("/api/inventory/v1/samples/" + basicSampleInfo.getId());

    // get full details of complex sample
    ApiSample complexSample =
        samplesApi.getSampleById(userSamples.getSamples().get(1).getId(), exampleContentUser);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_COMPLEX_SAMPLE_NAME, complexSample.getName());
    assertThat(complexSample.getAttachments()).isEmpty();

    // check attachment field of complex sample has a file attached to it
    ApiInventoryEntityField attachmentField = complexSample.getFields().get(6);
    assertEquals("MyAttachment", attachmentField.getName());
    assertNotNull(attachmentField.getAttachment());
    assertEquals("loremIpsem20para.txt", attachmentField.getAttachment().getName());
    assertEquals(10295L, attachmentField.getAttachment().getSize());
    assertThat(attachmentField.getAttachment().getLinks()).hasSize(2);
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
    assertThat(complexSample.getExtraFields()).hasSize(1);

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
    assertThat(updatedSample.getLinks()).hasSize(EXPECTED_SAMPLE_LINKS_COUNT);
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertEquals(complexSample, updatedSample);
    assertEquals("updated name", complexSample.getName());
    assertThat(complexSample.getExtraFields()).hasSize(2);

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
    assertThat(complexSample.getExtraFields()).hasSize(1);
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

    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    newSample.setTemplateId(savedTemplate.getId());

    // try changing template name through samples controller
    ApiSampleWithFullSubSamples sampleUpdate = new ApiSampleWithFullSubSamples();
    sampleUpdate.setName("updated name");
    Long savedTemplateId = savedTemplate.getId();
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                samplesApi.updateSample(
                    savedTemplateId, sampleUpdate, mockBindingResult, testUser));
    assertEquals("Please use /sampleTemplates endpoint for template actions", iae.getMessage());
  }

  @Test
  public void sampleDeleteRestoreDuplicateRejectTemplateIds() {
    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", testUser);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    Long templateId = savedTemplate.getId();

    IllegalArgumentException deleteError =
        assertThrows(
            IllegalArgumentException.class,
            () -> samplesApi.deleteSample(templateId, false, testUser));
    assertEquals(
        "Please use /sampleTemplates endpoint for template actions", deleteError.getMessage());

    IllegalArgumentException restoreError =
        assertThrows(
            IllegalArgumentException.class,
            () -> samplesApi.restoreDeletedSample(templateId, testUser));
    assertEquals(
        "Please use /sampleTemplates endpoint for template actions", restoreError.getMessage());

    IllegalArgumentException duplicateError =
        assertThrows(
            IllegalArgumentException.class, () -> samplesApi.duplicate(templateId, testUser));
    assertEquals(
        "Please use /sampleTemplates endpoint for template actions", duplicateError.getMessage());
  }

  @Test
  public void duplicateDoesNotLeakTemplateExistenceToUnauthorizedUser() {
    // The template must be genuinely private. testUser (created first in setUp) owns the lowest-id
    // sample templates and is therefore the default-templates-owner, whose templates are
    // world-readable; own this one with a later-created user so it is not a "default" template.
    User templateOwner = createInitAndLoginAnyUser();
    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", templateOwner);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    User otherUser = createInitAndLoginAnyUser();

    // the template-id guard must enforce permissions before revealing it is a template: a user
    // without access gets the read-path not-found rather than the 400 endpoint-mismatch leak
    Long savedTemplateId = savedTemplate.getId();
    assertThrows(NotFoundException.class, () -> samplesApi.duplicate(savedTemplateId, otherUser));
  }

  @Test
  public void createSampleFromInaccessibleTemplateIsRejected() {
    // owner must not be the default-templates-owner (testUser, created first in setUp), otherwise
    // the template is a world-readable "default" template and is not actually inaccessible
    User templateOwner = createInitAndLoginAnyUser();
    SampleTemplate sampleTemplate =
        recordFactory.createComplexSampleTemplate("API sample template", "API test", templateOwner);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(sampleTemplate);
    User otherUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples newSample =
        new ApiSampleWithFullSubSamples("from inaccessible template");
    newSample.setTemplateId(savedTemplate.getId());

    // a user without read access to the template must not be able to create a sample from it
    // (would otherwise be a permission bypass and a template-existence oracle)
    assertThrows(
        NotFoundException.class,
        () -> samplesApi.createNewSample(newSample, mockBindingResult, otherUser));
  }

  @Test
  public void checkRevisionHistoryMethods() throws Exception {
    // revisions are only created in real database transaction, this test just runs the code

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiInventoryRecordRevisionList revisions =
        samplesApi.getSampleAllRevisions(basicSample.getId(), testUser);
    assertThat(revisions.getRevisions()).isEmpty();

    // a missing revision surfaces as 404, not a 200 null body
    Long sampleId = basicSample.getId();
    assertThrows(
        NotFoundException.class, () -> samplesApi.getSampleRevision(sampleId, 1L, testUser));
  }

  @Test
  public void contentsHashSetInFileProperty() throws Exception {
    InputStream imageFile =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.base64");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());

    ApiSample updatedSample = updateSampleWithImage(imageBytes);

    assertEquals(
        PIC1_MAIN_IMAGE_CONTENT_HASH, updatedSample.getImageFileProperty().getContentsHash());
    assertEquals(
        PIC1_THUMBNAIL_CONTENT_HASH, updatedSample.getThumbnailFileProperty().getContentsHash());
  }

  @Test
  public void linksUrlsCreatedWithContentsHash() throws Exception {
    InputStream imageFile =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.base64");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());

    ApiSample updatedSample = updateSampleWithImage(imageBytes);

    String expectedFileEndpoint = "/api/inventory/v1/files/image/";
    assertTrue(
        assertLinksContainsUrl(
            updatedSample.getLinks(), expectedFileEndpoint + PIC1_MAIN_IMAGE_CONTENT_HASH));
    assertTrue(
        assertLinksContainsUrl(
            updatedSample.getLinks(), expectedFileEndpoint + PIC1_THUMBNAIL_CONTENT_HASH));
  }

  private ApiSample updateSampleWithImage(String image) throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user, "A Sample");

    sample.setNewBase64Image(image);
    return samplesApi.updateSample(sample.getId(), sample, mockBindingResult, user);
  }

  private boolean assertLinksContainsUrl(List<ApiLinkItem> links, String url) {
    return links.stream().map(ApiLinkItem::getLink).anyMatch(link -> link.contains(url));
  }

  @Test
  public void sameUserUploadingSameImageUsesSameFileProperty() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(user, "Sample 1");

    InputStream imageFile =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.base64");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());
    sample1.setNewBase64Image(imageBytes);
    ApiSample updatedSample1 =
        samplesApi.updateSample(sample1.getId(), sample1, mockBindingResult, user);

    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(user, "Sample 2");
    sample2.setNewBase64Image(imageBytes);
    ApiSample updatedSample2 =
        samplesApi.updateSample(sample2.getId(), sample2, mockBindingResult, user);

    assertEquals(
        updatedSample1.getImageFileProperty().getId(),
        updatedSample2.getImageFileProperty().getId());
    assertEquals(
        updatedSample1.getThumbnailFileProperty().getId(),
        updatedSample2.getThumbnailFileProperty().getId());
  }

  @Test
  public void differentUserUploadingSameImageGeneratesNewFileProperty() throws Exception {
    InputStream imageFile =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.base64");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());

    User user1 = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(user1, "Sample 1");
    sample1.setNewBase64Image(imageBytes);
    ApiSample updatedSample1 =
        samplesApi.updateSample(sample1.getId(), sample1, mockBindingResult, user1);

    User user2 = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(user2, "Sample 2");
    sample2.setNewBase64Image(imageBytes);
    ApiSample updatedSample2 =
        samplesApi.updateSample(sample2.getId(), sample2, mockBindingResult, user2);

    assertNotEquals(
        updatedSample1.getImageFileProperty().getId(),
        updatedSample2.getImageFileProperty().getId());
    assertNotEquals(
        updatedSample1.getThumbnailFileProperty().getId(),
        updatedSample2.getThumbnailFileProperty().getId());
  }
}
