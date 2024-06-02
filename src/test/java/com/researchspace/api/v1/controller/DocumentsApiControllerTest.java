package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.CREATED_DESC_API_PARAM;
import static com.researchspace.api.v1.controller.DocumentApiPaginationCriteria.LAST_MODIFIED_ASC_API_PARAM;
import static com.researchspace.api.v1.model.ApiLinkItem.NEXT_REL;
import static com.researchspace.api.v1.model.ApiLinkItem.PREV_REL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiDocumentSearchResult;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSearchQuery;
import com.researchspace.api.v1.model.ApiSearchQuery.OperatorEnum;
import com.researchspace.api.v1.model.ApiSearchTerm;
import com.researchspace.api.v1.model.ApiSearchTerm.QueryTypeEnum;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.MvcTestUtils;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.util.UriComponentsBuilder;

public class DocumentsApiControllerTest extends SpringTransactionalTest {

  private @Autowired DocumentsApiController documentsApi;
  private @Autowired RecordFavoritesManager favoritesMgr;
  private @Autowired RecordSigningManager signingMgr;
  protected @Autowired MvcTestUtils mvcUtils;

  protected API_ModelTestUtils apiModelTestUtils = new API_ModelTestUtils();
  private User testUser;

  BindingResult mockBindingResult = mock(BindingResult.class);

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
    MockServletContext servletContext = new MockServletContext();
    servletContext.setAttribute(UserSessionTracker.USERS_KEY, new UserSessionTracker());
    documentsApi.setServletContext(servletContext);
  }

  ApiDocumentSearchResult getDocs(
      DocumentApiPaginationCriteria pgcrit, ApiDocSearchConfig srchCfg, User user)
      throws BindException {
    return documentsApi.getDocuments(pgcrit, srchCfg, mockBindingResult, testUser);
  }

  @Test
  @Ignore
  public void testGetAllDocuments() throws Exception {

    // create 3 docs for our user
    StructuredDocument firstDoc = createBasicDocumentInRootFolderWithText(testUser, "first test");
    StructuredDocument secondDoc = createBasicDocumentInRootFolderWithText(testUser, "second test");
    StructuredDocument thirdDoc = createBasicDocumentInRootFolderWithText(testUser, "third test");

    Folder rootFolder = folderMgr.getRootFolderForUser(testUser);
    Notebook nbook = createNotebookWithNEntries(rootFolder.getId(), "testApiNB", 3, testUser);

    // marking 2nd doc and a notebook as a favorite
    favoritesMgr.saveFavoriteRecord(secondDoc.getId(), testUser.getId());
    favoritesMgr.saveFavoriteRecord(nbook.getId(), testUser.getId());

    // create another user, who shares doc with our user
    User piUser =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("apiOther"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);

    Group group = createGroup("apiGroup", piUser);
    addUsersToGroup(piUser, group, testUser);
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(piUser, "other");
    StructuredDocument otherTestDoc = createBasicDocumentInRootFolderWithText(piUser, "other test");
    shareRecordWithUser(piUser, otherDoc, testUser);
    shareRecordWithUser(piUser, otherTestDoc, testUser);

    flushToSearchIndices();

    // search without the search terms, should return all documents and entries, but skip notebook
    ApiDocSearchConfig defaultSearchConfig = new ApiDocSearchConfig();
    DocumentApiPaginationCriteria defaultPaginationCrit = new DocumentApiPaginationCriteria();
    ApiDocumentSearchResult allDocResults =
        getDocs(defaultPaginationCrit, defaultSearchConfig, testUser);
    assertNotNull(allDocResults);
    assertEquals(8, allDocResults.getTotalHits().longValue());
    assertEquals(0, allDocResults.getPageNumber().intValue());
    assertEquals(8, allDocResults.getDocuments().size());

    // search with favorites filter enabled
    defaultSearchConfig.setFilter("favorites");
    ApiDocumentSearchResult favoriteDocResults =
        getDocs(defaultPaginationCrit, defaultSearchConfig, testUser);
    assertNotNull(favoriteDocResults);
    assertEquals(1, favoriteDocResults.getTotalHits().longValue());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(
        favoriteDocResults.getDocuments().get(0), secondDoc);

    // search with a query provided
    ApiSearchQuery searchQuery = new ApiSearchQuery();
    // search for first doc's name - should return just that doc
    searchQuery.addTermsItem(new ApiSearchTerm(firstDoc.getName(), QueryTypeEnum.NAME));

    String jsonQuery = mvcUtils.getAsJsonString(searchQuery);
    ApiDocSearchConfig querySearchConfig = new ApiDocSearchConfig("", jsonQuery, "");
    querySearchConfig.setAdvancedQuery(jsonQuery);
    ApiDocumentSearchResult firstDocResults =
        getDocs(defaultPaginationCrit, querySearchConfig, testUser);
    assertNotNull(firstDocResults);
    assertEquals(1, firstDocResults.getTotalHits().longValue());
    assertEquals(0, firstDocResults.getPageNumber().intValue());

    List<ApiDocumentInfo> docInfoList = firstDocResults.getDocuments();
    assertEquals(1, docInfoList.size());
    assertEquals(firstDoc.getId(), docInfoList.get(0).getId());

    // search for 'test' text - should find 3 user's docs and one shared
    ApiSearchQuery query2 = new ApiSearchQuery();
    query2.addTermsItem(new ApiSearchTerm("test", QueryTypeEnum.FULLTEXT));
    String jsonQuery2 = mvcUtils.getAsJsonString(query2);
    ApiDocSearchConfig search2 = new ApiDocSearchConfig("", jsonQuery2, "");

    ApiDocumentSearchResult allResults = getDocs(defaultPaginationCrit, search2, testUser);
    assertEquals(4, allResults.getTotalHits().longValue());

    // combine with 'sharedWithMe' filter - should only find one shared doc
    search2.setFilter("sharedWithMe");
    ApiDocumentSearchResult sharedResults = getDocs(defaultPaginationCrit, search2, testUser);
    assertEquals(1, sharedResults.getTotalHits().longValue());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(
        sharedResults.getDocuments().get(0), otherTestDoc);

    // try with limit and sorting option
    search2.setFilter(null);
    DocumentApiPaginationCriteria pg3 =
        DocumentApiPaginationCriteria.builder()
            .pageSize(2)
            .orderBy(LAST_MODIFIED_ASC_API_PARAM)
            .build();
    ApiDocumentSearchResult twoLastModifiedResults = getDocs(pg3, search2, testUser);
    assertEquals(4, twoLastModifiedResults.getTotalHits().longValue());
    assertEquals(0, twoLastModifiedResults.getPageNumber().intValue());
    assertTrue(hasNextLink(twoLastModifiedResults));
    assertFalse(hasPrevLink(twoLastModifiedResults));

    List<ApiDocumentInfo> twoLastModifiedInfoList = twoLastModifiedResults.getDocuments();
    assertEquals(2, twoLastModifiedInfoList.size());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(twoLastModifiedInfoList.get(0), firstDoc);
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(twoLastModifiedInfoList.get(1), secondDoc);

    // get next page
    DocumentApiPaginationCriteria next = pg3.nextPage();
    ApiDocumentSearchResult nextResults = getDocs(next, search2, testUser);
    assertEquals(4, nextResults.getTotalHits().longValue());
    assertEquals(1, nextResults.getPageNumber().intValue());
    assertFalse(hasNextLink(nextResults));
    assertTrue(hasPrevLink(nextResults));

    List<ApiDocumentInfo> nextTwoLastModifiedInfoList = nextResults.getDocuments();
    assertEquals(2, nextTwoLastModifiedInfoList.size());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(nextTwoLastModifiedInfoList.get(0), thirdDoc);
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(
        nextTwoLastModifiedInfoList.get(1), otherTestDoc);

    // try advanced search, should find first and third document
    ApiSearchQuery query3 = new ApiSearchQuery();
    query3.setOperator(OperatorEnum.OR);
    query3.addTermsItem(new ApiSearchTerm(firstDoc.getName(), QueryTypeEnum.NAME));
    query3.addTermsItem(new ApiSearchTerm("third", QueryTypeEnum.FULLTEXT));
    String jsonQuery3 = mvcUtils.getAsJsonString(query3);

    // with pageSize = 1 and orderSize desc there should be just third doc on a first page
    ApiDocSearchConfig search4 = new ApiDocSearchConfig("", jsonQuery3, "");
    DocumentApiPaginationCriteria pg4 =
        DocumentApiPaginationCriteria.builder().pageSize(1).orderBy(CREATED_DESC_API_PARAM).build();
    ApiDocumentSearchResult lastCreatedResult = getDocs(pg4, search4, testUser);
    assertEquals(2, lastCreatedResult.getTotalHits().longValue());
    assertEquals(0, lastCreatedResult.getPageNumber().intValue());
    assertEquals(1, lastCreatedResult.getDocuments().size());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(
        lastCreatedResult.getDocuments().get(0), thirdDoc);

    DocumentApiPaginationCriteria pg5 =
        DocumentApiPaginationCriteria.builder()
            .pageSize(1)
            .pageNumber(1)
            .orderBy(CREATED_DESC_API_PARAM)
            .build();
    // and the first doc should be on a second page of the results
    ApiDocumentSearchResult lastCreatedSecondPageResult = getDocs(pg5, search4, testUser);
    assertEquals(2, lastCreatedSecondPageResult.getTotalHits().longValue());
    assertEquals(1, lastCreatedSecondPageResult.getPageNumber().intValue());
    assertEquals(1, lastCreatedSecondPageResult.getDocuments().size());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(
        lastCreatedSecondPageResult.getDocuments().get(0), firstDoc);
  }

  private boolean hasNextLink(ApiDocumentSearchResult results) {
    return results.getLinks().stream().anyMatch(link -> link.getRel().equals(NEXT_REL));
  }

  private boolean hasPrevLink(ApiDocumentSearchResult results) {
    return results.getLinks().stream().anyMatch(link -> link.getRel().equals(PREV_REL));
  }

  @Test
  public void testGetDocumentById() throws Exception {
    // creating a simple document, add a tag, mark as signed
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "apiTest");
    documentTagManager.apiSaveTagForDocument(basicDoc.getId(), "fav", testUser);
    signingMgr.signRecord(basicDoc.getId(), testUser, null, "signing");

    // reload the doc (to check assertions later)
    basicDoc = (StructuredDocument) recordMgr.get(basicDoc.getId());
    assertEquals("fav", basicDoc.getDocTag());
    assertTrue(basicDoc.isSigned());

    ApiDocument apiDoc = documentsApi.getDocumentById(basicDoc.getId(), testUser);
    assertNotNull(apiDoc);
    apiModelTestUtils.assertApiDocumentMatchSDoc(apiDoc, basicDoc);
    assertEquals("apiTest", apiDoc.getFields().get(0).getContent());
  }

  @Test
  public void testGetDocumentWithListOfMaterials() throws Exception {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "apiTest");
    Field docField = basicDoc.getFields().get(0);
    createBasicListOfMaterialsForUserAndDocField(testUser, docField, null);

    // reload the doc (to check assertions later)
    basicDoc = (StructuredDocument) recordMgr.get(basicDoc.getId());
    assertEquals(1, basicDoc.getFields().get(0).getListsOfMaterials().size());
    assertEquals(
        "basic list of materials",
        basicDoc.getFields().get(0).getListsOfMaterials().get(0).getName());
    assertEquals(0, basicDoc.getFields().get(0).getListsOfMaterials().get(0).getMaterials().size());

    ApiDocument apiDoc = documentsApi.getDocumentById(basicDoc.getId(), testUser);
    assertNotNull(apiDoc);
    apiModelTestUtils.assertApiDocumentMatchSDoc(apiDoc, basicDoc);
    assertEquals("apiTest", apiDoc.getFields().get(0).getContent());
    assertEquals(1, apiDoc.getFields().get(0).getListsOfMaterials().size());
    assertEquals(
        "basic list of materials",
        apiDoc.getFields().get(0).getListsOfMaterials().get(0).getName());

    //  add another LoM with a sample
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);
    createBasicListOfMaterialsForUserAndDocField(
        testUser, docField, List.of(new ApiMaterialUsage(sample, null)));

    // reload the doc (to check assertions later)
    basicDoc = (StructuredDocument) recordMgr.get(basicDoc.getId());
    assertEquals(2, basicDoc.getFields().get(0).getListsOfMaterials().size());
    assertEquals(1, basicDoc.getFields().get(0).getListsOfMaterials().get(1).getMaterials().size());

    apiDoc = documentsApi.getDocumentById(basicDoc.getId(), testUser);
    assertNotNull(apiDoc);
    apiModelTestUtils.assertApiDocumentMatchSDoc(apiDoc, basicDoc);
    assertEquals("apiTest", apiDoc.getFields().get(0).getContent());
    assertEquals(2, apiDoc.getFields().get(0).getListsOfMaterials().size());
  }

  protected String getAsJsonString(Object object) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(object);
  }

  @Test
  public void testLinkGeneration() throws Exception {
    ApiDocumentSearchResult results = new ApiDocumentSearchResult();
    results.setPageNumber(1);
    results.setTotalHits(193L);
    DocumentApiPaginationCriteria pg =
        DocumentApiPaginationCriteria.builder().pageNumber(1).pageSize(10).build();
    log.info("pgcrit is {}", pg);
    ApiDocSearchConfig cfg = new ApiDocSearchConfig();
    results.addNavigationLinks(UriComponentsBuilder.fromUriString("https://myrspace"), pg, cfg);

    assertTrue(
        results.getLinks().stream().anyMatch(link -> link.getRel().equals(ApiLinkItem.LAST_REL)));
    assertTrue(
        results.getLinks().stream().anyMatch(link -> link.getRel().equals(ApiLinkItem.NEXT_REL)));
    ApiLinkItem last =
        results.getLinks().stream()
            .filter(link -> link.getRel().equals(ApiLinkItem.LAST_REL))
            .findFirst()
            .get();
    assertEquals("https://myrspace/documents?pageSize=10&pageNumber=19", last.getLink());

    // another case: middle page for 5-page pagination
    results = new ApiDocumentSearchResult();
    results.setPageNumber(2);
    results.setTotalHits(50L);
    pg = DocumentApiPaginationCriteria.builder().pageNumber(2).pageSize(10).build();
    cfg = new ApiDocSearchConfig();
    results.addNavigationLinks(UriComponentsBuilder.fromUriString("https://myrspace"), pg, cfg);
    assertEquals(5, results.getLinks().size());

    last =
        results.getLinks().stream()
            .filter(link -> link.getRel().equals(ApiLinkItem.LAST_REL))
            .findFirst()
            .get();
    assertEquals("https://myrspace/documents?pageSize=10&pageNumber=4", last.getLink());
  }

  @Test(expected = NotFoundException.class)
  public void testGetDocumentByIdHandlesImageIDs() throws Exception {
    EcatImage image = addImageToGallery(testUser);
    documentsApi.getDocumentById(image.getId(), testUser);
  }

  @Test
  public void signedDocCannotBeModified_RSPAC1583() throws Exception {
    ApiDocument createdDoc =
        documentsApi.createNewDocument(new ApiDocument(), mockBindingResult, testUser);
    signingMgr.signRecord(createdDoc.getId(), testUser, null, "signing");
    ApiDocument secondRevApidDoc = new ApiDocument();
    secondRevApidDoc.setName("newName");

    assertExceptionThrown(
        () ->
            documentsApi.createNewRevision(
                createdDoc.getId(),
                secondRevApidDoc,
                new BeanPropertyBindingResult(createdDoc, "doc"),
                testUser),
        BindException.class);
  }

  @Test
  public void createUpdateSimpleNewDocument() throws Exception {

    // create structured document from empty api document
    ApiDocument createdDoc =
        documentsApi.createNewDocument(new ApiDocument(), mockBindingResult, testUser);
    assertNotNull(createdDoc.getId());
    assertNotNull(createdDoc.getOwner());
    assertEquals(testUser.getUsername(), createdDoc.getOwner().getUsername());
    assertEquals(StructuredDocument.DEFAULT_NAME, createdDoc.getName());
    assertEquals(1, createdDoc.getFields().size());
    assertNotNull(createdDoc.getFields().get(0).getId());
    assertEquals("", createdDoc.getFields().get(0).getContent());

    // create new revision: api document with new name and different field content
    ApiDocument secondRevApidDoc = new ApiDocument();
    secondRevApidDoc.setName("newName");
    ApiDocumentField newRevisionField = new ApiDocumentField();
    newRevisionField.setType(ApiField.ApiFieldType.TEXT);
    newRevisionField.setContent("updatedContent");
    secondRevApidDoc.getFields().add(newRevisionField);

    ApiDocument updatedDoc =
        documentsApi.createNewRevision(
            createdDoc.getId(), secondRevApidDoc, mockBindingResult, testUser);
    assertEquals("newName", updatedDoc.getName());
    assertEquals(1, updatedDoc.getFields().size());
    assertEquals("updatedContent", updatedDoc.getFields().get(0).getContent());

    // third revision: just a different field content
    ApiDocument thirdRevApiDoc = new ApiDocument();
    ApiDocumentField thirdRevisionField = new ApiDocumentField();
    thirdRevisionField.setId(updatedDoc.getFields().get(0).getId()); // providing matching field id
    thirdRevisionField.setType(ApiField.ApiFieldType.TEXT);
    thirdRevisionField.setContent("updatedContent2");
    thirdRevApiDoc.getFields().add(thirdRevisionField);

    updatedDoc =
        documentsApi.createNewRevision(
            createdDoc.getId(), thirdRevApiDoc, mockBindingResult, testUser);
    assertEquals("newName", updatedDoc.getName());
    assertEquals(1, updatedDoc.getFields().size());
    assertEquals("updatedContent2", updatedDoc.getFields().get(0).getContent());
  }

  @Test
  public void createNewDocumentWithImage() throws Exception {
    // upload image to Gallery, refer to it in text field content
    EcatImage image = addImageToGallery(testUser);
    String apiFieldContent = "attached image: <fileId=" + image.getId() + ">";
    String expectedSavedContentStart = "attached image: \n<p><img id=\"";

    ApiDocument apiDoc = new ApiDocument();
    ApiDocumentField apiField = new ApiDocumentField();
    apiField.setType(ApiField.ApiFieldType.TEXT);
    apiField.setContent(apiFieldContent);
    apiDoc.getFields().add(apiField);

    // create structured document from empty api document
    ApiDocument createdDoc = documentsApi.createNewDocument(apiDoc, mockBindingResult, testUser);
    assertNotNull(createdDoc.getId());
    String retrievedContent = createdDoc.getFields().get(0).getContent();
    assertTrue(
        "unexpected content: " + retrievedContent,
        retrievedContent.startsWith(expectedSavedContentStart));
  }

  @Test
  public void createNewDocInVariousParentFolderLocations() throws Exception {

    // when parentFolderId is not specified the default inbox is used
    ApiDocument defaultDoc =
        documentsApi.createNewDocument(new ApiDocument(), mockBindingResult, testUser);
    Folder defaultFolder = folderMgr.getFolder(defaultDoc.getParentFolderId(), testUser);
    assertTrue(defaultFolder.isApiInboxFolder());

    // create document explicitly in user's api folder
    ApiDocument docExplicitlyInApiFolder = new ApiDocument();
    docExplicitlyInApiFolder.setParentFolderId(defaultDoc.getParentFolderId());

    ApiDocument createdDocInApiFolder =
        documentsApi.createNewDocument(docExplicitlyInApiFolder, mockBindingResult, testUser);
    Folder apiFolder = folderMgr.getFolder(createdDocInApiFolder.getParentFolderId(), testUser);
    assertTrue(apiFolder.isApiInboxFolder());

    // create document in user's home folder
    ApiDocument docInHome = new ApiDocument();
    docInHome.setParentFolderId(testUser.getRootFolder().getId());

    ApiDocument createdDocInHome =
        documentsApi.createNewDocument(docInHome, mockBindingResult, testUser);
    Folder folderInHome = folderMgr.getFolder(createdDocInHome.getParentFolderId(), testUser);
    assertTrue(folderInHome.isRootFolderForUser(testUser));

    // try creating document in other user's folder
    User testUser2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser2);

    ApiDocument docInInvalidFolder = new ApiDocument();
    docInInvalidFolder.setParentFolderId(testUser2.getRootFolder().getId());
    try {
      documentsApi.createNewDocument(docInInvalidFolder, mockBindingResult, testUser);
      fail("exception expected when trying to create doc in other user's folder");
    } catch (AuthorizationException ae) {
      // expected
    }
  }

  @Test
  public void createUpdateExperimentDocument() throws Exception {

    RSForm experimentForm = recordFactory.createExperimentForm("ApiExperimentForm", "", testUser);
    experimentForm = formMgr.save(experimentForm, testUser);
    Long formId = experimentForm.getId();
    assertNotNull(formId);

    ApiFormInfo apiForm = new ApiFormInfo();
    apiForm.setId(formId);

    // providing only form id - should create empty document based on form
    ApiDocument emptyExpApiDoc = new ApiDocument();
    emptyExpApiDoc.setForm(apiForm);
    ApiDocument emptyExp =
        documentsApi.createNewDocument(emptyExpApiDoc, mockBindingResult, testUser);
    assertNotNull(emptyExp.getId());
    assertEquals(7, emptyExp.getFields().size());
    for (int i = 0; i < 7; i++) {
      assertEquals("", emptyExp.getFields().get(i).getContent());
    }

    // providing form id and fields - should create form-based document with content
    ApiDocument experimentApiDoc = new ApiDocument();
    experimentApiDoc.setForm(apiForm);

    List<ApiDocumentField> fields = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      ApiDocumentField apiField = new ApiDocumentField();
      apiField.setType(ApiField.ApiFieldType.TEXT);
      fields.add(apiField);
    }
    fields.get(0).setContent("test");
    experimentApiDoc.setFields(fields);

    ApiDocument expDoc =
        documentsApi.createNewDocument(experimentApiDoc, mockBindingResult, testUser);
    assertNotNull(expDoc.getId());
    assertEquals(7, expDoc.getFields().size());
    assertEquals("test", expDoc.getFields().get(0).getContent());
    for (int i = 1; i < 6; i++) {
      assertEquals("", expDoc.getFields().get(i).getContent());
    }

    // add all types of files to user's gallery. av not curently not supported.
    EcatImage image = addImageToGallery(testUser);
    EcatDocumentFile doc = addDocumentToGallery(testUser);
    // EcatAudio audio = addAudioFileToGallery(testUser);

    String imageMarker = "<fileId=" + image.getId() + ">";
    String docMarker = "<fileId=" + doc.getId() + ">";
    // String audioMarker = "<fileId=" + audio.getId() + ">";

    String expectedImageFragment = "class=\"imageDropped inlineImageThumbnail\"";
    String expectedDocFragment = "class=\"attachmentLinked\"";
    // String expectedAudioFragment = "class=\"audioDropped attachmentIcon\"";

    // updating fields 1-2 with attachments, field 4 with combined attachments, and field 6 with
    // text
    ApiDocumentField updatedField1 = new ApiDocumentField();
    updatedField1.setType(ApiField.ApiFieldType.TEXT);
    updatedField1.setContent(imageMarker);
    updatedField1.setId(expDoc.getFields().get(1).getId());
    ApiDocumentField updatedField2 = new ApiDocumentField();
    updatedField2.setType(ApiField.ApiFieldType.TEXT);
    updatedField2.setContent(docMarker);
    updatedField2.setId(expDoc.getFields().get(2).getId());
    ApiDocumentField updatedField4 = new ApiDocumentField();
    updatedField4.setType(ApiField.ApiFieldType.TEXT);
    updatedField4.setContent(imageMarker + docMarker);
    updatedField4.setId(expDoc.getFields().get(4).getId());
    ApiDocumentField updatedField6 = new ApiDocumentField();
    updatedField6.setType(ApiField.ApiFieldType.TEXT);
    updatedField6.setContent("updated");
    updatedField6.setId(expDoc.getFields().get(6).getId());

    ApiDocument updatedExperimentApiDoc = new ApiDocument();
    updatedExperimentApiDoc.getFields().add(updatedField1);
    updatedExperimentApiDoc.getFields().add(updatedField2);
    // updatedExperimentApiDoc.getFields().add(updatedField3);
    updatedExperimentApiDoc.getFields().add(updatedField4);
    updatedExperimentApiDoc.getFields().add(updatedField6);

    // update document with new attachments in fields 1-4 and new text in field 6
    ApiDocument updatedExp =
        documentsApi.createNewRevision(
            expDoc.getId(), updatedExperimentApiDoc, mockBindingResult, testUser);
    assertEquals(expDoc.getId(), updatedExp.getId());
    assertEquals(7, updatedExp.getFields().size());

    String imageHtmlFragment = updatedExp.getFields().get(1).getContent();
    String docAttachmentHtmlFragment = updatedExp.getFields().get(2).getContent();
    // String audioHtmlFragment = updatedExp.getFields().get(3).getContent();
    String combinedHtmlFragment = updatedExp.getFields().get(4).getContent();

    assertEquals("test", updatedExp.getFields().get(0).getContent());
    assertTrue("should contain image", imageHtmlFragment.contains(expectedImageFragment));
    // assertTrue("should contain audio", audioHtmlFragment.contains(expectedAudioFragment));
    assertTrue("should contain doc  ", docAttachmentHtmlFragment.contains(expectedDocFragment));
    assertTrue(
        "should contain image, audio and doc, was: " + combinedHtmlFragment,
        combinedHtmlFragment.contains(expectedImageFragment)
            // && combinedHtmlFragment.contains(expectedAudioFragment)
            && combinedHtmlFragment.contains(expectedDocFragment));
    assertEquals("", updatedExp.getFields().get(5).getContent());
    assertEquals("updated", updatedExp.getFields().get(6).getContent());
  }

  @Test
  public void createUpdateAllFieldTypesDocument() throws Exception {

    RSForm form =
        recordFactory.createFormForSeleniumTests("API all fields form", "for API tests", testUser);
    formDao.save(form);
    Long formId = form.getId();
    assertNotNull(formId);

    ApiFormInfo apiForm = new ApiFormInfo();
    apiForm.setId(formId);

    // providing only form id - should create new document based on form, with default values
    ApiDocument emptyAllFieldsDoc = new ApiDocument();
    emptyAllFieldsDoc.setForm(apiForm);
    ApiDocument emptyAllFields =
        documentsApi.createNewDocument(emptyAllFieldsDoc, mockBindingResult, testUser);
    assertNotNull(emptyAllFields.getId());
    assertEquals(7, emptyAllFields.getFields().size());
    assertEquals("2020-01-08", emptyAllFields.getFields().get(0).getContent());
    assertEquals("a,c", emptyAllFields.getFields().get(1).getContent());
    assertEquals("5.0", emptyAllFields.getFields().get(2).getContent());
    assertEquals("b", emptyAllFields.getFields().get(3).getContent());
    assertEquals("string", emptyAllFields.getFields().get(4).getContent());
    assertEquals("<p>MyText</p>", emptyAllFields.getFields().get(5).getContent());
    assertEquals("00:55", emptyAllFields.getFields().get(6).getContent());

    // creating list of updated fields.
    List<ApiDocumentField> fields = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      fields.add(new ApiDocumentField());
      fields.get(i).setId(emptyAllFields.getFields().get(i).getId());
    }
    fields.get(0).setType(ApiField.ApiFieldType.DATE);
    fields.get(0).setContent("2017-07-12");
    fields.get(1).setType(ApiField.ApiFieldType.CHOICE);
    fields.get(1).setContent("a,b");
    fields.get(2).setType(ApiField.ApiFieldType.NUMBER);
    fields.get(2).setContent("3.14");
    fields.get(3).setType(ApiField.ApiFieldType.RADIO);
    fields.get(3).setContent("a");
    fields.get(4).setType(ApiField.ApiFieldType.STRING);
    fields.get(4).setContent("anotherString");
    fields.get(5).setType(ApiField.ApiFieldType.TEXT);
    fields.get(5).setContent("text");
    fields.get(6).setType(ApiField.ApiFieldType.TIME);
    fields.get(6).setContent("09:01");

    // create new doc with new fields array
    ApiDocument updatedFieldsApiDoc = new ApiDocument();
    updatedFieldsApiDoc.setFields(fields);

    // updating empty doc with fields
    ApiDocument emptyDocUpdated =
        documentsApi.createNewRevision(
            emptyAllFields.getId(), updatedFieldsApiDoc, mockBindingResult, testUser);
    assertEquals(emptyAllFields.getId(), emptyDocUpdated.getId());
    assertEquals("2017-07-12", emptyDocUpdated.getFields().get(0).getContent());
    assertEquals("a,b", emptyDocUpdated.getFields().get(1).getContent());
    assertEquals("3.14", emptyDocUpdated.getFields().get(2).getContent());
    assertEquals("a", emptyDocUpdated.getFields().get(3).getContent());
    assertEquals("anotherString", emptyDocUpdated.getFields().get(4).getContent());
    assertEquals("text", emptyDocUpdated.getFields().get(5).getContent());
    assertEquals("09:01", emptyDocUpdated.getFields().get(6).getContent());

    // now let's create create new form-based document with content in one request
    ApiDocument secondApiDoc = new ApiDocument();
    secondApiDoc.setForm(apiForm);
    secondApiDoc.setFields(fields);

    ApiDocument allFieldsRetrieved =
        documentsApi.createNewDocument(secondApiDoc, mockBindingResult, testUser);
    assertNotNull(allFieldsRetrieved.getId());
    assertEquals(7, allFieldsRetrieved.getFields().size());
    assertEquals("2017-07-12", allFieldsRetrieved.getFields().get(0).getContent());
    assertEquals("a,b", allFieldsRetrieved.getFields().get(1).getContent());
    assertEquals("3.14", allFieldsRetrieved.getFields().get(2).getContent());
    assertEquals("a", allFieldsRetrieved.getFields().get(3).getContent());
    assertEquals("anotherString", allFieldsRetrieved.getFields().get(4).getContent());
    assertEquals("text", allFieldsRetrieved.getFields().get(5).getContent());
    assertEquals("09:01", allFieldsRetrieved.getFields().get(6).getContent());
  }

  @Test
  public void testApiFieldsConversionOnSavingNewRevision() {

    // create 3 fields with ids: 20, 21, 22
    List<Field> testDocFields = new ArrayList<>();
    testDocFields.add(new TextField());
    testDocFields.add(new TextField());
    testDocFields.add(new TextField());
    testDocFields.get(0).setId(20L);
    testDocFields.get(1).setId(21L);
    testDocFields.get(2).setId(22L);

    ApiDocument apiDocument = new ApiDocument();
    assertEquals(0, apiDocument.getFields().size());

    documentsApi.convertApiFieldsToMatchDocFields(apiDocument, testDocFields);
    assertEquals(
        "if fields are empty, conversion should leave them empty",
        0,
        apiDocument.getFields().size());

    // only one api field provided
    List<ApiDocumentField> oneApiField = new ArrayList<>();
    ApiDocumentField field = new ApiDocumentField();
    field.setId(21L);
    field.setContent("field21content");
    oneApiField.add(field);
    apiDocument.setFields(oneApiField);

    // conversion should put the field in right place and add empty surrounding fields
    documentsApi.convertApiFieldsToMatchDocFields(apiDocument, testDocFields);
    assertEquals(
        "if fields are omitted, conversion adds empty fields", 3, apiDocument.getFields().size());
    assertNull("empty apiField expected at index 0", apiDocument.getFields().get(0).getId());
    assertEquals("provided apiField expected at index 1", field, apiDocument.getFields().get(1));
    assertEquals(
        "provided apiField expected at index 1",
        field.getContent(),
        apiDocument.getFields().get(1).getContent());
    assertNull("empty apiField expected at index 2", apiDocument.getFields().get(2).getId());
  }

  @Test
  public void deleteDocByIdValidation() throws Exception {
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "apiTest");
    Notebook nb =
        createNotebookWithNEntries(
            folderDao.getRootRecordForUser(testUser).getId(), "any", 1, testUser);
    User other = createInitAndLoginAnyUser();
    assertExceptionThrown(
        () ->
            documentsApi.deleteDocumentById(basicDoc.getId(), other, new MockHttpServletResponse()),
        NotFoundException.class);
    logoutAndLoginAs(testUser);
    assertExceptionThrown(
        () -> documentsApi.deleteDocumentById(nb.getId(), testUser, new MockHttpServletResponse()),
        NotFoundException.class);
  }
}
