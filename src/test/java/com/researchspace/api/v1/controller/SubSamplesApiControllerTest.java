package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.model.ApiField.ApiFieldType.ATTACHMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.api.v1.InventoryFilesApi;
import com.researchspace.api.v1.SampleTemplatesApi;
import com.researchspace.api.v1.SamplesApi;
import com.researchspace.api.v1.SubSamplesApi;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.fieldmark.model.FieldmarkMultipartFile;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SubSamplesApiControllerTest extends SpringTransactionalTest {

  private static final int NUM_ATTACHEMENT_LINKS = 2;
  private @Autowired InventoryFilesApi inventoryFilesApi;
  private @Autowired SampleTemplatesApi sampleTemplatesApi;
  private @Autowired SamplesApi samplesApi;
  private @Autowired SubSamplesApi subSamplesApi;
  @Mock private DocumentTagManager documentTagManagerMock;

  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  final int EXPECTED_SS_LINK_COUNT = 3; // self + thumbnail + image

  @Before
  public void setUp() {
    openMocks(this);
    sampleDao.resetDefaultTemplateOwner();
    ReflectionTestUtils.setField(sampleApiMgr, "documentTagManager", documentTagManagerMock);
    ReflectionTestUtils.setField(subSampleApiMgr, "documentTagManager", documentTagManagerMock);
    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void createSubSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - create will write to ontology doc
    newSample = createSampleWithSubSamples("some tags");
    samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void deleteSubSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    ApiSubSample subSample1 = newSample.getSubSamples().get(0);
    subSamplesApi.deleteSubSample(subSample1.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - delete will write to ontology doc
    newSample = createSampleWithSubSamples("some tags");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    subSample1 = newSample.getSubSamples().get(0);
    subSamplesApi.deleteSubSample(subSample1.getId(), testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void restoreSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    ApiSubSample subSample1 = newSample.getSubSamples().get(0);
    subSamplesApi.deleteSubSample(subSample1.getId(), testUser);
    subSamplesApi.restoreDeletedSubSample(subSample1.getId(), testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - restore will write to ontology doc
    newSample = createSampleWithSubSamples("some tags");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
    subSample1 = newSample.getSubSamples().get(0);
    subSamplesApi.deleteSubSample(subSample1.getId(), testUser);
    verify(documentTagManagerMock, times(2)).updateUserOntologyDocument(eq(testUser));
    subSamplesApi.restoreDeletedSubSample(subSample1.getId(), testUser);
    verify(documentTagManagerMock, times(3)).updateUserOntologyDocument(eq(testUser));
  }

  @Test
  public void updateSubSampleShouldUpdateTags() throws Exception {
    User testUser = createInitAndLoginAnyUser();
    when(mockBindingResult.hasErrors()).thenReturn(false);
    ApiSampleWithFullSubSamples newSample = createSampleWithSubSamples("");
    newSample = samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    ApiSubSample subSample1 = newSample.getSubSamples().get(0);
    subSample1.setDescription("dddddd");
    subSamplesApi.updateSubSample(subSample1.getId(), subSample1, mockBindingResult, testUser);
    verify(documentTagManagerMock, never()).updateUserOntologyDocument(eq(testUser));
    // set tags - create will write to ontology doc
    subSample1.setApiTagInfo("some tags");
    subSamplesApi.updateSubSample(subSample1.getId(), subSample1, mockBindingResult, testUser);
    verify(documentTagManagerMock, times(1)).updateUserOntologyDocument(eq(testUser));
  }

  private ApiSampleWithFullSubSamples createSampleWithSubSamples(String tags) {
    ApiSampleWithFullSubSamples newBasicSample = new ApiSampleWithFullSubSamples();

    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setName("subSample WXY");
    newSubSample.setApiTagInfo(tags);
    newSubSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.MILLI_LITRE));
    ApiSubSample newSubSample2 = new ApiSubSample();
    newSubSample2.setName("subSample WXY #2");
    newSubSample2.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(25), RSUnitDef.MILLI_LITRE));
    newBasicSample.setSubSamples(Arrays.asList(newSubSample, newSubSample2));
    return newBasicSample;
  }

  @Test
  public void retrievePaginatedSubSampleList() throws BindException {
    /* let's create another user, as first one ever created may be creator of
     * default templates which would break pagination assertions */
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

    ApiSampleWithFullSubSamples newSample1 = new ApiSampleWithFullSubSamples("sample ABC");
    newSample1.setNewSampleSubSamplesCount(12);
    samplesApi.createNewSample(newSample1, mockBindingResult, testUser);

    ApiSampleWithFullSubSamples newSample2 = new ApiSampleWithFullSubSamples("sample XYZ");
    newSample2.setNewSampleSubSamplesCount(12);
    samplesApi.createNewSample(newSample2, mockBindingResult, testUser);

    // no pagination parameters
    ApiSubSampleSearchResult defaultSubSamples =
        subSamplesApi.getSubSamplesForUser(null, null, mockBindingResult, testUser);
    assertEquals(24, defaultSubSamples.getTotalHits().intValue());
    assertEquals(20, defaultSubSamples.getSubSamples().size());
    assertEquals(2, defaultSubSamples.getLinks().size());
    ApiSubSampleInfo firstSubSampleInfo = defaultSubSamples.getSubSamples().get(0);
    assertEquals("sample ABC.01", firstSubSampleInfo.getName());
    assertEquals(testUser.getFullName(), firstSubSampleInfo.getModifiedByFullName());
    assertEquals(EXPECTED_SS_LINK_COUNT, firstSubSampleInfo.getLinks().size());
    assertEquals("sample XYZ.08", defaultSubSamples.getSubSamples().get(19).getName());

    // third page, 5-items-per-page, reverse ordering
    InventoryApiPaginationCriteria apiPgCrit =
        new InventoryApiPaginationCriteria(2, 5, "name desc");
    ApiSubSampleSearchResult paginatedSubSamplesDesc =
        subSamplesApi.getSubSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(24, paginatedSubSamplesDesc.getTotalHits().intValue());
    assertEquals(5, paginatedSubSamplesDesc.getSubSamples().size());
    assertEquals("sample XYZ.02", paginatedSubSamplesDesc.getSubSamples().get(0).getName());
    assertEquals(5, paginatedSubSamplesDesc.getLinks().size());

    // first page, 5-items-per-page, globalId asc ordering
    apiPgCrit = new InventoryApiPaginationCriteria(0, 5, "globalId asc");
    paginatedSubSamplesDesc =
        subSamplesApi.getSubSamplesForUser(apiPgCrit, null, mockBindingResult, testUser);
    assertEquals(24, paginatedSubSamplesDesc.getTotalHits().intValue());
    assertEquals(5, paginatedSubSamplesDesc.getSubSamples().size());
    assertEquals("sample ABC.01", paginatedSubSamplesDesc.getSubSamples().get(0).getName());
    assertEquals("sample ABC.02", paginatedSubSamplesDesc.getSubSamples().get(1).getName());
    assertEquals(3, paginatedSubSamplesDesc.getLinks().size());
  }

  @Test
  public void createRetrieveSampleWithSubSamples() throws Exception {
    // creating sample template request
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("Sample Template");
    sampleTemplatePost.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    ApiSampleField templateField = new ApiSampleField();
    templateField.setName("attachement-field");
    templateField.setType(ATTACHMENT);
    templateField.setColumnIndex(1);
    sampleTemplatePost.setFields(List.of(templateField));
    ApiSampleTemplate sampleTemplate =
        sampleTemplatesApi.createNewSampleTemplate(sampleTemplatePost, mockBindingResult, testUser);

    // creating sample request
    ApiSampleWithFullSubSamples newBasicSample = new ApiSampleWithFullSubSamples();
    ApiSampleField attachementField = new ApiSampleField();
    attachementField.setDeleteFieldRequest(false);
    attachementField.setDeleteFieldOnSampleUpdate(false);
    attachementField.setMandatory(false);
    attachementField.setColumnIndex(1);
    attachementField.setName("attachement-field");
    attachementField.setType(ATTACHMENT);
    newBasicSample.setFields(List.of(attachementField));
    newBasicSample.setTemplateId(sampleTemplate.getId());

    ApiSubSample newSubSample = new ApiSubSample();
    newSubSample.setName("subSample WXY");
    newSubSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.MILLI_LITRE));
    ApiSubSample newSubSample2 = new ApiSubSample();
    newSubSample2.setName("subSample WXY #2");
    newSubSample2.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(25), RSUnitDef.MILLI_LITRE));
    newBasicSample.setSubSamples(Arrays.asList(newSubSample, newSubSample2));

    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newBasicSample, mockBindingResult, testUser);
    assertNotNull(createdSample);

    // upload attachement
    byte[] imageFile = IOUtils.resourceToByteArray("/TestResources/IS1.jpg");
    inventoryFilesApi.uploadFile(
        new FieldmarkMultipartFile(imageFile, "IS1.jpg"),
        new ApiInventoryFilePost(createdSample.getFields().get(0).getGlobalId(), "IS1.jpg"),
        testUser);

    // subsamples
    assertEquals(2, createdSample.getSubSamples().size());
    ApiSubSample createdSubSample =
        subSamplesApi.getSubSampleById(createdSample.getSubSamples().get(0).getId(), testUser);
    assertNotNull(createdSubSample.getId());
    assertEquals(newSubSample.getName(), createdSubSample.getName());
    assertEquals(newSubSample.getQuantity(), createdSubSample.getQuantity());
    assertEquals(0, createdSubSample.getNotes().size());
    assertEquals(
        NUM_ATTACHEMENT_LINKS,
        createdSubSample.getSampleInfo().getFields().get(0).getAttachment().getLinks().size(),
        "The attachment links for have not been correctly created");

    assertEquals(EXPECTED_SS_LINK_COUNT, createdSubSample.getLinks().size());
    assertTrue(createdSubSample.getLinks().get(0).getLink().contains("/subSamples/"));
    ApiSubSample createdSubSample2 =
        subSamplesApi.getSubSampleById(createdSample.getSubSamples().get(1).getId(), testUser);
    assertNotNull(createdSubSample2.getId());
    assertEquals(newSubSample2.getName(), createdSubSample2.getName());
    assertEquals(newSubSample2.getQuantity(), createdSubSample2.getQuantity());
    assertEquals(0, createdSubSample2.getNotes().size());
    assertEquals(
        NUM_ATTACHEMENT_LINKS,
        createdSubSample2.getSampleInfo().getFields().get(0).getAttachment().getLinks().size(),
        "The attachment links for have not been correctly created");

    ApiSubSample retrievedSubSample =
        subSamplesApi.getSubSampleById(createdSubSample.getId(), testUser);
    assertEquals(createdSubSample, retrievedSubSample);
    ApiSubSample retrievedSubSample2 =
        subSamplesApi.getSubSampleById(createdSubSample2.getId(), testUser);
    assertEquals(createdSubSample2, retrievedSubSample2);

    // add another note to 2nd subsample
    ApiSubSampleNote anotherNote = new ApiSubSampleNote("another");
    ApiSubSample updatedSubSample2 =
        subSamplesApi.addSubSampleNote(
            retrievedSubSample2.getId(), anotherNote, mockBindingResult, testUser);
    retrievedSubSample2 = subSamplesApi.getSubSampleById(createdSubSample2.getId(), testUser);
    assertEquals(updatedSubSample2, retrievedSubSample2);
    assertEquals(1, retrievedSubSample2.getNotes().size());
    assertEquals(
        testUser.getUsername(), retrievedSubSample2.getNotes().get(0).getCreatedBy().getUsername());

    ApiSampleInfo parentSample = retrievedSubSample2.getSampleInfo();
    assertNotNull(parentSample);
    assertEquals(createdSample.getGlobalId(), parentSample.getGlobalId());
  }

  @Test
  public void updateDefaultBasicSubSample() throws Exception {
    User userWithContent = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(userWithContent);
    logoutAndLoginAs(userWithContent);

    // find subsample of default sample
    ApiSampleSearchResult userSamples =
        samplesApi.getSamplesForUser(null, null, mockBindingResult, userWithContent);
    assertEquals(2, userSamples.getTotalHits().intValue());
    ApiSampleInfo complexSampleInfo = userSamples.getSamples().get(1);
    ApiSample complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertEquals(1, complexSample.getSubSamples().size());
    ApiSubSample complexSubSample =
        subSamplesApi.getSubSampleById(
            complexSample.getSubSamples().get(0).getId(), userWithContent);
    assertEquals(1, complexSubSample.getExtraFields().size());

    // update subsample name, add extra numeric field
    ApiSubSample subSampleUpdate = new ApiSubSample();
    subSampleUpdate.setName("updated name");
    ApiExtraField extraFieldAddition = new ApiExtraField(ExtraFieldTypeEnum.NUMBER);
    extraFieldAddition.setNewFieldRequest(true);
    extraFieldAddition.setContent("3.14");
    subSampleUpdate.getExtraFields().add(extraFieldAddition);

    ApiSubSample updatedSubSample =
        subSamplesApi.updateSubSample(
            complexSubSample.getId(), subSampleUpdate, mockBindingResult, userWithContent);
    assertEquals(EXPECTED_SS_LINK_COUNT, updatedSubSample.getLinks().size());
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    complexSubSample = subSamplesApi.getSubSampleById(complexSubSample.getId(), userWithContent);
    assertEquals(complexSubSample, updatedSubSample);
    assertEquals("updated name", complexSubSample.getName());
    assertEquals(2, complexSubSample.getExtraFields().size());

    // delete both extra fields
    ApiExtraField extraFieldDeletion1 = new ApiExtraField();
    extraFieldDeletion1.setId(complexSubSample.getExtraFields().get(0).getId());
    extraFieldDeletion1.setDeleteFieldRequest(true);
    ApiExtraField extraFieldDeletion2 = new ApiExtraField();
    extraFieldDeletion2.setId(complexSubSample.getExtraFields().get(1).getId());
    extraFieldDeletion2.setDeleteFieldRequest(true);
    subSampleUpdate = new ApiSubSample();
    subSampleUpdate.getExtraFields().add(extraFieldDeletion1);
    subSampleUpdate.getExtraFields().add(extraFieldDeletion2);

    updatedSubSample =
        subSamplesApi.updateSubSample(
            complexSubSample.getId(), subSampleUpdate, mockBindingResult, userWithContent);
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    complexSubSample = subSamplesApi.getSubSampleById(complexSubSample.getId(), userWithContent);
    assertEquals(complexSubSample, updatedSubSample);
    assertEquals(0, complexSubSample.getExtraFields().size());

    // delete subsample
    subSamplesApi.deleteSubSample(complexSubSample.getId(), userWithContent);
    complexSubSample = subSamplesApi.getSubSampleById(complexSubSample.getId(), userWithContent);
    assertTrue(complexSubSample.isDeleted());

    // deleted subsample shouldn't be displayed in api sample listing
    complexSample = samplesApi.getSampleById(complexSampleInfo.getId(), userWithContent);
    assertTrue(complexSample.getSubSamples().isEmpty());
  }

  @Test
  public void moveSubSampleBetweenContainers() throws Exception {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 4, 4);
    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);
    ApiContainer workbench = getWorkbenchForUser(testUser);

    // subsample to move around
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    assertEquals(1, createdSample.getSubSamples().size());
    ApiSubSampleInfo subSampleInfo = createdSample.getSubSamples().get(0);
    ApiSubSample movingSubSample = subSamplesApi.getSubSampleById(subSampleInfo.getId(), testUser);
    assertEquals("WORKBENCH", movingSubSample.getParentContainer().getCType());
    assertEquals(1, movingSubSample.getParentContainers().size());
    assertEquals(workbench.getId(), movingSubSample.getParentContainers().get(0).getId());

    // move to list container
    ApiSubSample updateRequest = new ApiSubSample();
    updateRequest.setParentContainer(listContainer);
    movingSubSample =
        subSamplesApi.updateSubSample(
            subSampleInfo.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(listContainer.getId(), movingSubSample.getParentContainer().getId());
    assertEquals(2, movingSubSample.getParentContainers().size());
    assertEquals(listContainer.getId(), movingSubSample.getParentContainers().get(0).getId());
    assertEquals(workbench.getId(), movingSubSample.getParentContainers().get(1).getId());

    // verify target container updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());

    // move to grid container
    updateRequest.setParentContainer(gridContainer);
    updateRequest.setParentLocation(new ApiContainerLocation(2, 3));
    movingSubSample =
        subSamplesApi.updateSubSample(
            subSampleInfo.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(gridContainer.getId(), movingSubSample.getParentContainer().getId());
    assertEquals(2, movingSubSample.getParentLocation().getCoordX());
    assertEquals(3, movingSubSample.getParentLocation().getCoordY());

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    gridContainer = containerApiMgr.getApiContainerById(gridContainer.getId(), testUser);
    assertEquals(1, gridContainer.getContentSummary().getTotalCount());

    // move to image container
    updateRequest.setParentContainer(imageContainer);
    updateRequest.setParentLocation(imageContainer.getLocations().get(0));
    movingSubSample =
        subSamplesApi.updateSubSample(
            subSampleInfo.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(imageContainer.getId(), movingSubSample.getParentContainer().getId());

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(1, imageContainer.getContentSummary().getTotalCount());

    // move back to list container
    updateRequest.setParentContainer(listContainer);
    updateRequest.setParentLocation(null);
    movingSubSample =
        subSamplesApi.updateSubSample(
            subSampleInfo.getId(), updateRequest, mockBindingResult, testUser);
    assertEquals(listContainer.getId(), movingSubSample.getParentContainer().getId());

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
  }

  @Test
  public void checkRevisionHistoryMethods() throws Exception {
    // revisions are only created in real database transaction, this test just runs the code

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample subSample = basicSample.getSubSamples().get(0);
    ApiInventoryRecordRevisionList revisions =
        subSamplesApi.getSubSampleAllRevisions(subSample.getId(), testUser);
    assertEquals(0, revisions.getRevisions().size());

    ApiSubSample subSampleRevision =
        subSamplesApi.getSubSampleRevision(subSample.getId(), 1L, testUser);
    assertNull(subSampleRevision);
  }
}
