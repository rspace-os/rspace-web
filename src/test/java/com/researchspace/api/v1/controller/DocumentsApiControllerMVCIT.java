package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.ApiDocSearchConfig.MAX_QUERY_LENGTH;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiDocumentSearchResult;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSearchQuery;
import com.researchspace.api.v1.model.ApiSearchTerm;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.InternalLinkManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestPropertySource(properties = "chemistry.web.url=http://howler.researchspace.com:8099")
@RunWith(ConditionalTestRunner.class)
public class DocumentsApiControllerMVCIT extends API_MVC_TestBase {

  @Autowired DocumentsApiController docApiCtrller;
  @Autowired RecordEditorTracker tracker;
  @Autowired InternalLinkManager internalLinkMgr;
  MockServletContext mockServletCtxt = null;

  @Before
  public void setup() throws Exception {
    super.setUp();
    mockServletCtxt = new MockServletContext();
    mockServletCtxt.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    docApiCtrller.setServletContext(mockServletCtxt);
  }

  @Test
  public void searchForDocumentsInvalidPaginationIsHandled() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    createBasicDocumentInRootFolderWithText(anyUser, "test");

    MvcResult invalidQuery1 =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .param("pageSize", "-1"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(invalidQuery1, ApiError.class);
    assertEquals("pageSize: Page size must be between 1 and 100.", error.getErrors().get(0));

    MvcResult invalidQuery2 =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .param("query", randomAlphabetic(MAX_QUERY_LENGTH + 1)))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(invalidQuery2, ApiError.class);
    assertEquals("query: Max query length is 2000.", error.getErrors().get(0));

    MvcResult invalidQuery3 =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .param("pageNumber", "-1"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(invalidQuery3, ApiError.class);
    assertEquals("pageNumber: Page number must be 0 or greater.", error.getErrors().get(0));

    MvcResult invalidQuery4 =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .param("pageNumber", "11"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiDocumentSearchResult noDocs =
        getFromJsonResponseBody(invalidQuery4, ApiDocumentSearchResult.class);
    assertEquals(0, noDocs.getDocuments().size()); // 11th page is empty
  }

  @Test
  public void searchForDocuments() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    StructuredDocument testDoc = createBasicDocumentInRootFolderWithText(anyUser, "first test");
    createBasicDocumentInRootFolderWithText(anyUser, "second test");
    createBasicDocumentInRootFolderWithText(anyUser, "third one");

    // no search term - should return all documents
    MvcResult noSearchTermResult =
        this.mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/documents", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(noSearchTermResult.getResolvedException());

    ApiDocumentSearchResult allDocs =
        getFromJsonResponseBody(noSearchTermResult, ApiDocumentSearchResult.class);
    assertNotNull(allDocs);
    assertEquals(3, allDocs.getTotalHits().intValue());

    // simple search for word 'test', should return first two docs
    MvcResult simpleSearchResult =
        this.mockMvc
            .perform(
                get("/api/v1/documents")
                    .param("query", "test")
                    .principal(anyUser::getUsername)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(simpleSearchResult.getResolvedException());

    ApiDocumentSearchResult simpleSearchDocs =
        getFromJsonResponseBody(simpleSearchResult, ApiDocumentSearchResult.class);
    assertNotNull(simpleSearchDocs);
    assertEquals(2, simpleSearchDocs.getTotalHits().intValue());

    // advanced searching for 'apiTest' in text, should return testDoc
    String queryJson = "{ \"terms\":[ { \"query\": \"first\", \"queryType\": \"fullText\" } ]}";
    MvcResult result =
        this.mockMvc
            .perform(
                get("/api/v1/documents")
                    .param("orderBy", "lastModified asc")
                    .param("advancedQuery", queryJson)
                    .principal(anyUser::getUsername)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());

    log.info(result.getResponse().getContentAsString());
    ApiDocumentSearchResult apiDocs =
        getFromJsonResponseBody(result, ApiDocumentSearchResult.class);
    assertNotNull(apiDocs);
    assertEquals(1, apiDocs.getTotalHits().intValue());
    apiModelTestUtils.assertApiDocumentInfoMatchSDoc(apiDocs.getDocuments().get(0), testDoc);
  }

  @Test
  public void searchForDocumentsByRecord() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    StructuredDocument otherDocument =
        createBasicDocumentInRootFolderWithText(anyUser, "targetText");
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));
    // search empty folder
    ApiSearchQuery query = new ApiSearchQuery();
    query.addTermsItem(
        new ApiSearchTerm(targetFolder.getGlobalIdentifier(), ApiSearchTerm.QueryTypeEnum.RECORDS));
    query.addTermsItem(new ApiSearchTerm("targetText", ApiSearchTerm.QueryTypeEnum.FULLTEXT));

    String queryJson = JacksonUtil.toJson(query);
    assertSearchHits(apiKey, queryJson, 0);

    // now there is one doc in folder
    logoutAndLoginAs(anyUser);
    StructuredDocument document = createBasicDocumentInFolder(anyUser, targetFolder, "targetText");
    assertSearchHits(apiKey, queryJson, 1);

    // search for a doc outside the folder, should miss it now
    query.getTerms().get(1).setQuery(otherDocument.getName() + "");
    assertSearchHits(apiKey, JacksonUtil.toJson(query), 0);
  }

  @Test
  public void foldersNotReturned() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));

    MvcResult result =
        this.mockMvc
            .perform(get("/api/v1/documents").header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();
  }

  @Test
  public void searchForDocumentsByDate() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    StructuredDocument testDoc = createBasicDocumentInRootFolderWithText(anyUser, "target");
    String queryFormat =
        "{ \"operator\": \"and\", \"terms\":[ { \"query\": \"%s\", \"queryType\": \"%s\" },"
            + " { \"query\": \"%s\", \"queryType\": \"name\" } ]}";
    String previousTimeString = "2014-01-01";
    String futureTimeString = "2087-01-01";
    // from date success
    String queryJson =
        String.format(queryFormat, previousTimeString + ";", "created", testDoc.getName());
    assertSearchHits(apiKey, queryJson, 1);

    // from date no results
    queryJson = String.format(queryFormat, futureTimeString + ";", "created", testDoc.getName());
    assertSearchHits(apiKey, queryJson, 0);

    // to date success
    queryJson = String.format(queryFormat, ";" + futureTimeString, "created", testDoc.getName());
    assertSearchHits(apiKey, queryJson, 1);

    // to date no results
    queryJson = String.format(queryFormat, ";" + previousTimeString, "created", testDoc.getName());
    assertSearchHits(apiKey, queryJson, 0);

    // other formats:
    queryJson = String.format(queryFormat, ";" + "2007-12-12Z", "created", testDoc.getName());

    // date range
    queryJson =
        String.format(
            queryFormat, previousTimeString + ";" + futureTimeString, "created", testDoc.getName());
    assertSearchHits(apiKey, queryJson, 1);
  }

  private void assertSearchHits(String apiKey, String queryJson, int expectedResults)
      throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                get("/api/v1/documents").header("apiKey", apiKey).param("advancedQuery", queryJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiDocumentSearchResult apiDocs =
        getFromJsonResponseBody(result, ApiDocumentSearchResult.class);
    assertEquals(expectedResults, apiDocs.getTotalHits().intValue());
  }

  @Test
  public void getDocumentCSVById() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "apiTest");

    MvcResult result =
        this.mockMvc
            .perform(
                getDocById(anyUser, apiKey, doc.getId())
                    // set Accept to drive CSV creation
                    .accept(BaseApiController.CSV))
            .andReturn();
    assertNull(result.getResolvedException());
    String csv = result.getResponse().getContentAsString();
    // single field + header = 2 rows; 6 properties of Field
    API_ModelTestUtils.assertRowAndColumnCount(csv, 2, 6);
  }

  @Test
  public void getDocumentById() throws Exception {
    TestGroup tg1 = createTestGroup(2);
    User anyUser = tg1.getUserByPrefix("u1");
    String apiKey = createApiKeyForuser(anyUser);
    logoutAndLoginAs(anyUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "apiTest");

    shareRecordWithGroup(anyUser, tg1.getGroup(), doc);
    Field textField = doc.getFields().get(0);
    addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), textField, anyUser);

    MvcResult result = this.mockMvc.perform(getDocById(anyUser, apiKey, doc.getId())).andReturn();
    System.err.println(result.getResolvedException());
    assertNull(result.getResolvedException());

    ApiDocument apiDoc = getFromJsonResponseBody(result, ApiDocument.class);
    assertNotNull(apiDoc);
    assertNotNull(apiDoc.getParentFolderId());
    assertEquals(1, getMediaFileCount(apiDoc));
    ApiFile file = apiDoc.getFields().get(0).getFiles().get(0);
    assertTrue(
        "No self link for file " + file.getId(),
        file.getLinks().stream().anyMatch(link -> link.getRel().equals(ApiLinkItem.SELF_REL)));
    assertTrue(
        "No self link for file " + file.getId(),
        file.getLinks().stream().anyMatch(link -> link.getRel().equals(ApiLinkItem.ENCLOSURE_REL)));
    apiModelTestUtils.assertApiDocumentMatchSDoc(apiDoc, doc);

    // now simulate user deleting the attachment in UI
    logoutAndLoginAs(anyUser);
    doAutosaveAndSaveMVC(textField, "", anyUser);
    RSpaceTestUtils.logout();
    // now retrieve again
    result =
        this.mockMvc
            .perform(getDocById(anyUser, apiKey, doc.getId()))
            .andExpect(status().isOk())
            .andReturn();
    apiDoc = getFromJsonResponseBody(result, ApiDocument.class);
    assertEquals(0, getMediaFileCount(apiDoc));

    // now login as u2. Can get document, but parentFolder is null, as they are not the owner.
    User user2 = tg1.getUserByPrefix("u2");
    String apiKey2 = createApiKeyForuser(user2);
    MvcResult result2 = this.mockMvc.perform(getDocById(user2, apiKey2, doc.getId())).andReturn();
    apiDoc = getFromJsonResponseBody(result2, ApiDocument.class);
    assertNull(apiDoc.getParentFolderId());
  }

  @Test
  public void getDocumentWithListOfMaterials() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    logoutAndLoginAs(anyUser);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "apiTest");
    Field docField = doc.getFields().get(0);
    ApiContainer invContainer = createBasicContainerForUser(anyUser);
    ApiMaterialUsage containerUsage = new ApiMaterialUsage(invContainer, null);
    ApiSampleWithFullSubSamples invBasicSample = createBasicSampleForUser(anyUser);
    ApiMaterialUsage basicSampleUsage = new ApiMaterialUsage(invBasicSample, null);
    ApiSampleWithFullSubSamples invComplexSample = createComplexSampleForUser(anyUser);
    ApiMaterialUsage complexSubSampleUsage =
        new ApiMaterialUsage(
            invComplexSample.getSubSamples().get(0),
            new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_LITRE));
    ApiSampleWithFullSubSamples customSample = createBasicSampleTemplateAndSample(anyUser);
    ApiMaterialUsage customSampleUsage = new ApiMaterialUsage(customSample, null);

    List<ApiMaterialUsage> usedItems =
        List.of(containerUsage, basicSampleUsage, complexSubSampleUsage, customSampleUsage);
    createBasicListOfMaterialsForUserAndDocField(anyUser, docField, usedItems);

    MvcResult result = this.mockMvc.perform(getDocById(anyUser, apiKey, doc.getId())).andReturn();
    assertNull(result.getResolvedException());

    ApiDocument apiDoc = getFromJsonResponseBody(result, ApiDocument.class);
    assertNotNull(apiDoc);
    assertNotNull(apiDoc.getParentFolderId());
    List<ApiListOfMaterials> lomInApiDoc = apiDoc.getFields().get(0).getListsOfMaterials();
    assertEquals(1, lomInApiDoc.size());
    assertEquals(4, lomInApiDoc.get(0).getMaterials().size());
    ApiInventoryRecordInfo usedContainer = lomInApiDoc.get(0).getMaterials().get(0).getRecord();
    assertEquals("listContainer", usedContainer.getName());
    ApiInventoryRecordInfo usedSample = lomInApiDoc.get(0).getMaterials().get(1).getRecord();
    assertEquals("mySample", usedSample.getName());
    ApiInventoryRecordInfo usedSubSample = lomInApiDoc.get(0).getMaterials().get(2).getRecord();
    assertEquals("mySubSample", usedSubSample.getName());
    assertEquals("1 ml", usedSubSample.getQuantity().toQuantityInfo().toPlainString());
    ApiInventoryRecordInfo usedCustomSample = lomInApiDoc.get(0).getMaterials().get(3).getRecord();
    assertEquals("sample from junit test template", usedCustomSample.getName());

    // RSDEV-127, ensure that LoM item that was moved between containers doesn't cause problems
    ApiContainer otherInvContainer1 = createBasicContainerForUser(anyUser);
    ApiContainer otherInvContainer2 = createBasicContainerForUser(anyUser);
    moveContainerIntoListContainer(invContainer.getId(), otherInvContainer1.getId(), anyUser);
    moveContainerIntoListContainer(invContainer.getId(), otherInvContainer2.getId(), anyUser);

    result = this.mockMvc.perform(getDocById(anyUser, apiKey, doc.getId())).andReturn();
    assertNull(result.getResolvedException());
  }

  private MockHttpServletRequestBuilder getDocById(User anyUser, String apiKey, Long docId) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/documents/{id}", anyUser, docId);
  }

  private int getMediaFileCount(ApiDocument apiDoc) {
    return apiDoc.getFields().get(0).getFiles().size();
  }

  @Test
  public void notFoundExceptionThrownIfResourceNotExistsOrNotAuthorised() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    User otherUser = createInitAndLoginAnyUser();
    logoutAndLoginAs(otherUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(otherUser, "apiTest");
    EcatDocumentFile galleryFile = addDocumentToGallery(otherUser);
    logout();
    String apiKey = createApiKeyForuser(anyUser);
    // not-existent
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/documents/12345", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not permitted
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/documents/" + doc.getId(), anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // exists, but wrong type: RSPAC1141
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/documents/" + galleryFile.getId(), anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // authentication failure returned in preference to 404 if apikey wrong:
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, "WRONG KEY", "/documents/" + galleryFile.getId(), anyUser))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  public void createUpdateDeleteBasicDocument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    RSpaceTestUtils.logout();
    String emptyDocJSON = "{}";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, emptyDocJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiDocument docFromEmptyRequest = getFromJsonResponseBody(result, ApiDocument.class);
    Long emptyDocId = docFromEmptyRequest.getId();
    assertNotNull(emptyDocId);
    assertEquals(StructuredDocument.DEFAULT_NAME, docFromEmptyRequest.getName());
    assertEquals("Basic Document", docFromEmptyRequest.getForm().getName());

    String newNameJSON = "{ \"name\": \"updatedName\"}";
    result =
        this.mockMvc
            .perform(
                createBuilderForPut(API_VERSION.ONE, apiKey, "/documents/{id}", anyUser, emptyDocId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(newNameJSON))
            .andExpect(status().isOk())
            .andReturn();
    ApiDocument docFromRenameRequest = getFromJsonResponseBody(result, ApiDocument.class);
    assertEquals(emptyDocId, docFromRenameRequest.getId());
    assertEquals("updatedName", docFromRenameRequest.getName());

    String simpleDocJSON =
        "{ \"name\": \"My Experiment\", \"tags\": \"API\", \"fields\": [  { \"content\": \"<p> Add"
            + " 2ul 50uM EDTA to Xenopus egg extract. <p> See <a"
            + " href=\\\"https://a.b.com\\\">here</a>\" } ] }";

    result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(simpleDocJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiDocument docFromSimpleRequest = getFromJsonResponseBody(result, ApiDocument.class);
    Long simpleDocId = docFromSimpleRequest.getId();
    assertNotNull(simpleDocId);
    assertEquals("My Experiment", docFromSimpleRequest.getName());
    assertEquals("API", docFromSimpleRequest.getTags());
    assertEquals("Basic Document", docFromSimpleRequest.getForm().getName());
    assertEquals(
        "<p>Add 2ul 50uM EDTA to Xenopus egg extract.</p>\n"
            + "<p>See <a href=\"https://a.b.com\">here</a></p>",
        docFromSimpleRequest.getFields().get(0).getContent());

    String newContentJSON =
        "{ \"fields\": [ { \"type\": \"text\", \"content\": \"updatedContent\" } ] }";
    result =
        this.mockMvc
            .perform(
                createBuilderForPut(
                        API_VERSION.ONE, apiKey, "/documents/{id}", anyUser, simpleDocId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(newContentJSON))
            .andExpect(status().isOk())
            .andReturn();
    ApiDocument docFromUpdateRequest = getFromJsonResponseBody(result, ApiDocument.class);
    assertEquals(simpleDocId, docFromUpdateRequest.getId());
    assertEquals("My Experiment", docFromUpdateRequest.getName());
    assertEquals("API", docFromUpdateRequest.getTags());
    assertEquals("updatedContent", docFromUpdateRequest.getFields().get(0).getContent());

    // now delete it:
    this.mockMvc
        .perform(createBuilderForDelete(apiKey, "/documents/{id}", anyUser, simpleDocId))
        .andExpect(status().isNoContent());
    // assert is deleted.
    Boolean deleted = doInTransaction(() -> documentIsDeleted(simpleDocId));
    assertTrue(deleted);
    // and no edit lock:
    assertFalse(tracker.isEditing(recordMgr.get(simpleDocId)).isPresent());
    // and can't retrieve:
    this.mockMvc.perform(getDocById(anyUser, apiKey, simpleDocId)).andExpect(status().isNotFound());
  }

  private boolean documentIsDeleted(Long simpleDocId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from StructuredDocument where id =:id", StructuredDocument.class)
        .setParameter("id", simpleDocId)
        .uniqueResult()
        .isDeleted();
  }

  @Test
  public void createAllFieldTypesDocument() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    RSForm apiForm =
        recordFactory.createFormForSeleniumTests("API all fields form", "for API tests", anyUser);
    apiForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
    formMgr.save(apiForm, anyUser);

    String emptyDocJSON = String.format("{ \"form\" : { \"id\": \"%s\"} }", apiForm.getId());
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(emptyDocJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiDocument docFromEmptyRequest = getFromJsonResponseBody(result, ApiDocument.class);
    assertNotNull(docFromEmptyRequest.getId());
    assertEquals("API all fields form", docFromEmptyRequest.getForm().getName());
    assertEquals(7, docFromEmptyRequest.getFields().size());
    for (int i = 0; i < 7; i++) {
      if (i == 1) {
        // field 1 is choice field, and its value is passed to api client in simplified format
        assertEquals("a,c", docFromEmptyRequest.getFields().get(i).getContent());
      } else {
        assertEquals(
            apiForm.getFieldForms().get(i).getDefault(),
            docFromEmptyRequest.getFields().get(i).getContent());
      }
    }

    String formDocJSON =
        String.format(
            "{ \"form\" : { \"globalId\" : \"FM%s\" }, \"fields\": [ {}, {}, {}, {}, {}, {"
                + " \"type\": \"text\", \"content\": \"<p> Add 2ul 50uM EDTA to Xenopus egg"
                + " extract. <p> See <a href=\\\"https://a.b.com\\\">here</a>\" }, {} ] }",
            apiForm.getId());
    result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/documents", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(formDocJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiDocument docFromSimpleRequest = getFromJsonResponseBody(result, ApiDocument.class);
    assertNotNull(docFromSimpleRequest.getId());
    assertEquals(7, docFromEmptyRequest.getFields().size());
    for (int i = 0; i < 7; i++) {
      if (i == 1) {
        assertEquals("a,c", docFromEmptyRequest.getFields().get(i).getContent());
      } else if (i == 5) {
        assertEquals(
            "<p>Add 2ul 50uM EDTA to Xenopus egg extract.</p>\n"
                + "<p>See <a href=\"https://a.b.com\">here</a></p>",
            docFromSimpleRequest.getFields().get(5).getContent());
      } else {
        assertEquals(
            apiForm.getFieldForms().get(i).getDefault(),
            docFromSimpleRequest.getFields().get(i).getContent());
      }
    }
  }

  @Test
  public void createFolderDocLinks() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    Folder anyFolder = createSubFolder(getRootFolderForUser(anyUser), "subfolder", anyUser);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(anyUser, "linkTarget");
    String content =
        String.format(
            "<p> protocol <docId=%d> EDTA to Xenopus  extract see: folder <docId=%d>",
            sd.getId(), anyFolder.getId());
    ApiDocument post = createDefaultOKBasicDocumentPost();
    post.getFields().get(0).setContent(content);
    String apiKey = createApiKeyForuser(anyUser);
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, post))
            .andReturn();
    ApiDocument created = getFromJsonResponseBody(result, ApiDocument.class);
    // assert link to SD is made.
    assertEquals(1, internalLinkMgr.getLinksPointingToRecord(sd.getId()).size());
  }

  @Test
  public void createNewDocumentErrorMessages() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // problem with validating ApiDocument model (name too long)
    String tooLongName = StringUtils.leftPad("test", 256, '*');
    String json = "{ \"name\": \"" + tooLongName + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isBadRequest())
            .andReturn();
    Exception exception = result.getResolvedException();
    assertTrue(exception.getMessage().contains("Name cannot be longer than 255 characters"));

    // basic doc with field provided, but content pointing to unaccessible attachment
    json = "{ \"fields\" : [ { \"content\": \"my attachment <fileId=1234567890>\" } ] }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Attachment with fileId=1234567890 from field 1 cannot be accessed",
        exception.getMessage());

    // content pointing to audio/video attachment, but linking av is not currently supported
    EcatAudio audio = addAudioFileToGallery(anyUser);
    json = "{ \"fields\" : [ { \"content\": \"my attachment <fileId=" + audio.getId() + ">\" } ] }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Linking to audio/video attachments is not supported in current version of RSpace API "
            + "(fileId "
            + audio.getId()
            + ")",
        exception.getMessage());

    // create all fields form to test other errors
    RSForm apiForm =
        recordFactory.createFormForSeleniumTests("API all fields form", "for API tests", anyUser);
    apiForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
    formMgr.save(apiForm, anyUser);

    // wrong form id
    json = "{ \"form\" : { \"id\": \"-11\"} }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Form with id [-11] could not be retrieved - possibly it has been deleted, "
            + "does not exist, or you do not have permission to access it.",
        exception.getMessage());

    // wrong format of form globalId
    json = "{ \"form\" : { \"globalId\": \"11\"} }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals("Wrong format of provided form.globalId [11]", exception.getMessage());

    // form id and globalId passed, but they don't match
    json = "{ \"form\" : { \"id\": \"11\", \"globalId\": \"FM12\"} } }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals("form.id [11] doesn't match form.globalId [FM12]", exception.getMessage());

    // fields provided, but wrong number
    json = String.format("{ \"form\" : { \"id\": \"%s\"}, \"fields\" : [ {} ] }", apiForm.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals("\"fields\" array should have 7 fields, but had 1", exception.getMessage());

    // field type provided, but wrong
    json =
        String.format(
            "{ \"form\" : { \"id\": \"%s\"}, \"fields\" : [ { \"type\":\"time\", \"content\":"
                + " \"12:15\" }, {}, {}, {}, {}, {}, {} ] }",
            apiForm.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Mismatched field type in field 1: \"date\" type expected, but was \"time\"",
        exception.getMessage());

    // field name provided, but wrong
    json =
        String.format(
            "{ \"form\" : { \"id\": \"%s\"}, \"fields\" : [ { \"name\":\"field1\", \"content\":"
                + " \"12:15\" }, {}, {}, {}, {}, {}, {} ] }",
            apiForm.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Mismatched field name in field 1 \"field1\": \"MyDate\" name expected, but was \"field1\"",
        exception.getMessage());

    // field provided, but content not valid
    json =
        String.format(
            "{ \"form\" : { \"id\": \"%s\"}, \"fields\" : [ { \"type\": \"date\", \"content\":"
                + " \"12:15\" }, {}, {}, {}, {}, {}, {} ] }",
            apiForm.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    exception = result.getResolvedException();
    assertEquals(
        "Validation problem with field 1: Invalid date format [12:15]", exception.getMessage());

    EcatImage image = addImageToGallery(anyUser);
    // finally, a correct call
    int initialLinkCount = mediaMgr.getIdsOfLinkedDocuments(image.getId()).size();

    json =
        String.format(
            "{ \"form\" : { \"id\": \"%s\"}, \"fields\" : [ { \"type\": \"date\", \"content\":"
                + " \"12-07-2017\" }, {}, {}, {}, {},  { \"type\": \"text\", \"content\":"
                + " \"<fileId=%s>\"}, {} ] }",
            apiForm.getId(), image.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andExpect(status().isCreated())
            .andReturn();
    // assert that media-field link is established
    assertEquals(initialLinkCount + 1, mediaMgr.getIdsOfLinkedDocuments(image.getId()).size());
  }

  @Test
  public void createNewDocumentInApiFolder() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiDocument doc = createDefaultOKBasicDocumentPost();

    String json = JacksonUtil.toJson(doc);
    final long initialAPiFolderCount = getApiFolderCountForUser(anyUser);
    assertApiFolderForUserPresent(
        anyUser, true); // may need to fix this when not created by default
    this.mockMvc
        .perform(
            createBuilderForPost(API_VERSION.ONE, apiKey, "/documents", anyUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated())
        .andReturn();
    // may need to fix this when not created by default
    final long finalApiFolderCount = getApiFolderCountForUser(anyUser);
    assertEquals(initialAPiFolderCount, finalApiFolderCount);
  }

  @Test
  public void createNewDocumentInChosenTargetFolder() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    Folder newTarget = createSubFolder(getRootFolderForUser(anyUser), "subfolder", anyUser);
    assertEquals(0, getRecordCountInFolderForUser(newTarget.getId()));
    ApiDocument doc = createDefaultOKBasicDocumentPost();
    doc.setParentFolderId(newTarget.getId());
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, doc))
            .andReturn();
    ApiDocument created = getFromJsonResponseBody(result, ApiDocument.class);
    assertEquals(1, getRecordCountInFolderForUser(newTarget.getId()));
    assertEquals(newTarget.getId(), created.getParentFolderId());
  }

  private Long getApiFolderCountForUser(User anyUser) throws Exception {
    return doInTransaction(() -> getApiFolderCount(anyUser));
  }

  private Long getApiFolderCount(User anyUser) {
    return (Long)
        sessionFactory
            .getCurrentSession()
            .createCriteria(Folder.class)
            .add(Restrictions.eq("editInfo.name", Folder.API_INBOX_FOLDER_NAME))
            .add(Restrictions.eq("owner", anyUser))
            .setProjection(Projections.rowCount())
            .uniqueResult();
  }

  private void assertApiFolderForUserPresent(User anyUser, boolean present) throws Exception {
    if (!present) {
      doInTransaction(
          () ->
              assertFalse(
                  folderDao
                      .getApiFolderForContentType(anyUser.getUsername(), anyUser)
                      .isPresent()));
    } else {
      doInTransaction(
          () ->
              assertTrue(
                  folderDao
                      .getApiFolderForContentType(anyUser.getUsername(), anyUser)
                      .isPresent()));
    }
  }

  private ApiDocument createDefaultOKBasicDocumentPost() {
    ApiDocument doc = new ApiDocument();
    doc.setName("anyDoc");
    ApiDocumentField field = new ApiDocumentField();
    field.setType(ApiFieldType.TEXT);
    field.setContent("anyContent");
    doc.setFields(toList(field));
    return doc;
  }

  @Test
  public void editDocumentErrorMessages() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "testDoc1");

    // problem with validating ApiDocument model (tags too long)
    String tooLongTags = StringUtils.leftPad("tag", 8001, '*');
    String json = "{ \"tags\": \"" + tooLongTags + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPut(
                        API_VERSION.ONE, apiKey, "/documents/{id}", anyUser, doc.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isBadRequest())
            .andReturn();
    Exception exception = result.getResolvedException();
    assertTrue(
        exception.getMessage().contains("Document tags cannot be longer than 8000 characters"));

    // create a document and request edit lock
    EditStatus editStatus = recordMgr.requestRecordEdit(doc.getId(), anyUser, activeUsers);
    assertEquals(EditStatus.EDIT_MODE, editStatus);

    // api user shoudn't be able to update when anyone holds edit lock
    String newNameJSON = "{ \"name\": \"updatedName\"}";
    result =
        this.mockMvc
            .perform(
                createBuilderForPut(
                        API_VERSION.ONE, apiKey, "/documents/{id}", anyUser, doc.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(newNameJSON))
            .andExpect(status().isConflict())
            .andReturn();
    exception = result.getResolvedException();
    assertNotNull(exception);
    assertEquals(
        "Document " + doc.getId() + " is currently edited by " + anyUser.getUsername(),
        exception.getMessage());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testInsertingChemistryFileViaAPI() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    EcatChemistryFile chemistryFile = addChemistryFileToGallery("Aminoglutethimide.mol", anyUser);
    // basic doc with field provided, but content pointing to unaccessible attachment
    String json =
        String.format(
            "{ \"fields\" : [ { \"content\": \"my attachment <fileId=%d>\" } ] }",
            chemistryFile.getId());
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/documents", anyUser, json))
            .andReturn();
    ApiDocument created = getFromJsonResponseBody(result, ApiDocument.class);
    assertNotNull(created.getId());
    assertEquals(1, created.getFields().size());
    String actual = created.getFields().get(0).getContent();
    Document doc = Jsoup.parse(actual);
    Elements elements = doc.select("img.chem");
    assertEquals(
        "unexpected number of chem replacements, actual content: \n" + actual, 1, elements.size());
    assertTrue(elements.first().select("img.chem").hasAttr("data-chemfileid"));
  }
}
