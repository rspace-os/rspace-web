package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.JacksonUtil.toJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormFieldPost;
import com.researchspace.api.v1.model.ApiForm;
import com.researchspace.api.v1.model.ApiFormField;
import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.api.v1.model.ApiFormSearchResult;
import com.researchspace.api.v1.model.ApiStringFormField;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindException;

@WebAppConfiguration
public class FormsApiControllerMVCIT extends API_MVC_TestBase {

  User anyUser, otherUser;
  String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void testGetPaginatedForms() throws Exception {

    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/forms", anyUser))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiFormSearchResult apiForms = getFromJsonResponseBody(result, ApiFormSearchResult.class);
    assertNotNull(apiForms);
    assertTrue(apiForms.getForms().size() > 0);

    ApiFormInfo apiForm = apiForms.getForms().get(0);
    assertNotNull(apiForm.getName());
    assertEquals(1, apiForm.getLinks().size());

    // 11 in total now
    IntStream.range(0, 10).forEach(i -> createAnyForm(piUser));

    result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/forms", anyUser)
                    .param("pageNumber", "0")
                    .param("pageSize", "3")
                    .param("orderBy", "created asc")
                    .param("scope", "all"))
            .andReturn();
    assertNull(result.getResolvedException());

    apiForms = getFromJsonResponseBody(result, ApiFormSearchResult.class);
    assertNotNull(apiForms);
    assertEquals(3, apiForms.getForms().size());
  }

  @Test
  public void testGetFormByIdAndDelete() throws Exception {
    // create a new form
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();

    String jsonBody = JacksonUtil.toJson(formPost);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/forms", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody))
            .andReturn();
    ApiForm apiForm = getFromJsonResponseBody(result, ApiForm.class);
    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/forms/{id}", anyUser, apiForm.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiForm apiFormGet = getFromJsonResponseBody(result2, ApiForm.class);
    assertEquals(apiForm.getFields().size(), apiFormGet.getFields().size());
    assertEquals(4, apiForm.getLinks().size());
    // other user unauthorised
    otherUser = createInitAndLoginAnyUser();
    String otherUserKey = createNewApiKeyForUser(otherUser);
    mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, otherUserKey, "/forms/{id}", otherUser, apiForm.getId()))
        .andExpect(status().isUnauthorized());

    // delete
    mockMvc
        .perform(createBuilderForDelete(apiKey, "/forms/{id}", anyUser, apiForm.getId()))
        .andExpect(status().isNoContent());
    assertExceptionThrown(() -> formMgr.get(apiForm.getId(), anyUser), DataAccessException.class);
    mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/forms/{id}", anyUser, apiForm.getId()))
        .andExpect(status().isNotFound())
        .andReturn();
    // delete again, get 404
    mockMvc
        .perform(createBuilderForDelete(apiKey, "/forms/{id}", anyUser, apiForm.getId()))
        .andExpect(status().isNotFound());
  }

  @Test
  public void testGetSeleniumFormById() throws Exception {
    RSForm seleniumForm =
        formMgr.getAll().stream()
            .filter(t -> t.getName().equals(ContentInitializerForDevRunManager.SELENIUM_FORM_NAME))
            .findAny()
            .get();

    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/forms/{id}", anyUser, seleniumForm.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiForm apiForm = getFromJsonResponseBody(result2, ApiForm.class);
    assertEquals(seleniumForm.getFieldForms().size(), apiForm.getFields().size());
  }

  @Test
  public void testPostInValidFormIsRejected() throws Exception {
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    // name is a required property, checkc that validation is working properly
    formPost.getFields().get(0).setName(null);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/forms", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(formPost)))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertException(result, BindException.class);
  }

  @Test
  public void publishUnpublishShareUnshareForm() throws Exception {
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/forms", anyUser, formPost))
            .andReturn();
    ApiForm apiForm = getFromJsonResponseBody(result, ApiForm.class);

    String publishUrl = extractLinkByRel(apiForm, "publish");
    MvcResult publishedResult = putUrl(publishUrl);
    ApiForm publishedForm = getFromJsonResponseBody(publishedResult, ApiForm.class);
    assertEquals(FormState.PUBLISHED, publishedForm.getFormState());

    String unpublishUrl = extractLinkByRel(publishedForm, "publish");
    MvcResult unpublishedResult = putUrl(unpublishUrl);
    ApiForm unpublishedForm = getFromJsonResponseBody(unpublishedResult, ApiForm.class);
    assertEquals(FormState.UNPUBLISHED, unpublishedForm.getFormState());
    // now share and unshare
    String shareUrl = extractLinkByRel(unpublishedForm, "share");
    MvcResult sharedResult = putUrl(shareUrl);
    ApiForm sharedForm = getFromJsonResponseBody(sharedResult, ApiForm.class);
    assertEquals(PermissionType.READ, sharedForm.getAccessControl().getGroupPermissionType());
    assertEquals(PermissionType.NONE, sharedForm.getAccessControl().getWorldPermissionType());
    // global share
    String globalshareUrl =
        extractLinkByRel(unpublishedForm, "share").replace("share", "shareglobal");
    MvcResult globalsharedResult = putUrl(globalshareUrl, createNewSysAdminApiKey());
    ApiForm globalsharedForm = getFromJsonResponseBody(globalsharedResult, ApiForm.class);
    assertEquals(PermissionType.READ, globalsharedForm.getAccessControl().getGroupPermissionType());
    assertEquals(PermissionType.READ, globalsharedForm.getAccessControl().getWorldPermissionType());

    String unshareUrl = extractLinkByRel(sharedForm, "share");
    MvcResult unsharedResult = putUrl(unshareUrl);
    ApiForm unsharedForm = getFromJsonResponseBody(unsharedResult, ApiForm.class);
    assertEquals(PermissionType.NONE, unsharedForm.getAccessControl().getGroupPermissionType());
  }

  private String extractLinkByRel(ApiForm apiForm, String rel) throws URISyntaxException {
    return new URI(
            apiForm.getLinks().stream()
                .filter(link -> link.getRel().equals(rel))
                .findFirst()
                .get()
                .getLink())
        .getPath();
  }

  private MvcResult putUrl(String formPutUrl) throws Exception {
    return mockMvc
        .perform(put(formPutUrl).principal(createPrincipal(anyUser)).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult putUrl(String formPutUrl, String apiKeyForUser) throws Exception {
    return mockMvc
        .perform(
            put(formPutUrl).principal(createPrincipal(anyUser)).header("apiKey", apiKeyForUser))
        .andExpect(status().isOk())
        .andReturn();
  }

  // rspac-2103
  @Test
  public void testSetAndRetrieveFormIcon() throws Exception {
    String formName = getRandomAlphabeticString("");
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    formPost.setName(formName);

    ApiForm apiForm = postValidForm(formPost);
    long iconId = formMgr.get(apiForm.getId(), anyUser).getIconId();
    MockMultipartFile iconFile =
        new MockMultipartFile(
            "file", "biggerLogo.png", "img/png", getTestResourceFileStream("biggerLogo.png"));
    // initial form has -1, which retrieves default image.
    assertEquals(-1, apiForm.getIconId().intValue());

    MvcResult result =
        mockMvc
            .perform(
                fileUpload(createUrl(API_VERSION.ONE, "/forms/{id}/icon"), apiForm.getId())
                    .file(iconFile)
                    .principal(createPrincipal(anyUser))
                    .header("apiKey", apiKey))
            .andExpect(status().isCreated())
            .andReturn();
    ApiForm formWithUpdatedIcon = getFromJsonResponseBody(result, ApiForm.class);
    assertTrue(
        formWithUpdatedIcon.getLinks().stream().anyMatch(link -> link.getRel().equals("icon")));

    Long iconId2 = formMgr.get(apiForm.getId(), anyUser).getIconId();

    assertTrue(iconId2 != iconId);
    assertEquals(iconId2, formWithUpdatedIcon.getIconId());
    // get
    result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/forms/{id}/icon/{iconId}",
                    anyUser,
                    formWithUpdatedIcon.getId(),
                    formWithUpdatedIcon.getIconId()))
            .andReturn();
    int actualLength = result.getResponse().getContentAsByteArray().length;
    // expected size of the icon, differs slightly between java 8 and 11
    assertTrue("uexpected length: " + actualLength, actualLength > 1750 && actualLength < 1950);
  }

  @Test
  public void testPostValidForm() throws Exception {
    String formName = getRandomAlphabeticString("");
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    formPost.setName(formName);

    ApiForm apiForm = postValidForm(formPost);
    log.info("{}", apiForm);
    assertEquals(9, apiForm.getFields().size());
    assertEquals(formPost.getName(), apiForm.getName());

    // create a document with the new form
    logoutAndLoginAs(anyUser);
    StructuredDocument doc =
        createDocumentInRootFolder(formMgr.get(apiForm.getId(), anyUser), anyUser);
    // this is default text from the form
    assertEquals("fieldChoices=c1", doc.getFields().get(0).getFieldData());
    assertEquals("some text", doc.getFields().get(1).getFieldData());
    assertEquals("some string", doc.getFields().get(2).getFieldData());
    assertEquals("22.0", doc.getFields().get(3).getFieldData());
    assertEquals("r1", doc.getFields().get(4).getFieldData());
    assertEquals(
        LocalDate.now().format(DateTimeFormatter.ISO_DATE), doc.getFields().get(5).getFieldData());

    publishForm(apiForm);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/forms", anyUser)
                    .param("query", formName))
            .andReturn();
    ApiFormSearchResult apiForms = getFromJsonResponseBody(result, ApiFormSearchResult.class);
    assertEquals(1, apiForms.getTotalHits().intValue());
    assertEquals(apiForm.getId(), apiForms.getForms().get(0).getId());
  }

  private ApiForm postValidForm(FormTemplatesCommon.FormPost formPost) throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/forms", anyUser, formPost))
            .andExpect(status().isCreated())
            .andReturn();
    return getFromJsonResponseBody(result, ApiForm.class);
  }

  private void publishForm(ApiForm normalApiForm) throws URISyntaxException, Exception {
    String publishUrl = extractLinkByRel(normalApiForm, "publish");
    putUrl(publishUrl);
  }

  @Test
  public void testEditingANewUnpublishedForm() throws Exception {
    // set up a form - this is a 'new' form
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    int originalFieldCount = formPost.getFields().size();
    ApiForm apiForm = postANewForm(formPost);

    // now, re-use the post object but set in ids, as if we are saving it:
    for (int i = 0; i < apiForm.getFields().size(); i++) {
      long id = apiForm.getFields().get(i).getId();
      formPost.getFields().get(i).setId(id);
    }
    formPost.setName("NEWNAME");
    formPost.setTags("NEWTAG");
    // add a field - we are editing a 'new' form so we are updating the original form, there is no
    // temp copy created.
    FormTemplatesCommon.StringFieldPost stringFF = createAStringField();
    formPost.getFields().add(2, stringFF);
    ApiForm apiForm2 = doFormEditPut(formPost, apiForm.getId());
    assertEquals(formPost.getName(), apiForm2.getName());
    assertEquals(formPost.getTags(), apiForm2.getTags());
    assertEquals(originalFieldCount + 1, apiForm2.getFields().size());
    // assert the new field is added in the correct place
    assertEquals("sff2", apiForm2.getFields().get(2).getName());

    // now lets delete 2 fields. The new string field previously added is now the first field
    // setting up the post data....
    formPost.getFields().remove(0);
    formPost.getFields().remove(0);
    // sanity check
    assertEquals("sff2", formPost.getFields().get(0).getName());
    // and remember to set the id for the field we are going to post back....
    formPost.getFields().get(0).setId(apiForm2.getFields().get(2).getId());

    // now save. We should remove two fields.
    ApiForm apiForm3 = doFormEditPut(formPost, apiForm.getId());
    assertEquals(originalFieldCount - 1, apiForm3.getFields().size());
    assertEquals("sff2", apiForm3.getFields().get(0).getName());
  }

  @Test
  public void testEditingAPublishedForm() throws Exception {
    // set up a published form:
    FormTemplatesCommon.FormPost formPost = FormTemplatesCommonTest.createValidFormPost();
    List<String> expectedNames =
        formPost.getFields().stream().map(FormFieldPost::getName).collect(Collectors.toList());
    int originalFieldCount = formPost.getFields().size();
    ApiForm returnedApiForm = postANewForm(formPost);
    // publish the form
    String publishUrl = extractLinkByRel(returnedApiForm, "publish");
    MvcResult publishedResult = putUrl(publishUrl);

    // now, re-use the post object but set in ids, as we are saving a new version:
    updateFormPostIds(formPost, returnedApiForm);
    // -------------- adding a field---------------//
    FormTemplatesCommon.StringFieldPost stringFF = createAStringField();
    formPost.getFields().add(2, stringFF);
    expectedNames.add(2, stringFF.getName());
    ApiForm apiFormWithAddedField = doFormEditPut(formPost, returnedApiForm.getId());
    assertEquals(originalFieldCount + 1, apiFormWithAddedField.getFields().size());
    // assert the new field is added in the correct place
    assertEquals("sff2", apiFormWithAddedField.getFields().get(2).getName());
    assertEquals(FormState.PUBLISHED, apiFormWithAddedField.getFormState());
    assertEquals(1, apiFormWithAddedField.getVersion().intValue());
    assertFieldOrder(apiFormWithAddedField, expectedNames);
    updateFormPostIds(formPost, apiFormWithAddedField);

    // -------------- deleting a field---------------//
    // now lets delete 2 fields. The new string field previously added is now the
    // first field
    // setting up the post data....
    formPost.getFields().remove(0);
    formPost.getFields().remove(0);
    expectedNames.remove(0);
    expectedNames.remove(0);
    // sanity check
    assertEquals("sff2", formPost.getFields().get(0).getName());

    // and remember to set the id for the field we are going to post back....
    formPost.getFields().get(0).setId(apiFormWithAddedField.getFields().get(2).getId());

    // now save. We should remove two fields.
    ApiForm apiForm3_fieldsRemoved = doFormEditPut(formPost, apiFormWithAddedField.getId());

    assertEquals(FormState.PUBLISHED, apiForm3_fieldsRemoved.getFormState());
    assertEquals(2, apiForm3_fieldsRemoved.getVersion().intValue());
    assertEquals(originalFieldCount - 1, apiForm3_fieldsRemoved.getFields().size());
    // this is now the first field in the form.
    assertEquals("sff2", apiForm3_fieldsRemoved.getFields().get(0).getName());
    assertFieldOrder(apiForm3_fieldsRemoved, expectedNames);

    // -------------- editing a field---------------//
    updateFormPostIds(formPost, apiForm3_fieldsRemoved);
    // replace the string field at index 0 with a new version.
    FormTemplatesCommon.StringFieldPost updatedStringField = createAStringField();
    updatedStringField.setDefaultValue("ABCDE");
    updatedStringField.setName("XYZ123");
    updatedStringField.setId(formPost.getFields().get(0).getId());
    formPost.getFields().set(0, updatedStringField);
    expectedNames.set(0, "XYZ123");

    ApiForm apiForm4_fieldEdited = doFormEditPut(formPost, apiForm3_fieldsRemoved.getId());
    assertEquals("XYZ123", apiForm4_fieldEdited.getFields().get(0).getName());
    assertEquals(
        "ABCDE", ((ApiStringFormField) apiForm4_fieldEdited.getFields().get(0)).getDefaultValue());
    assertEquals(originalFieldCount - 1, apiForm4_fieldEdited.getFields().size());
    assertFieldOrder(apiForm4_fieldEdited, expectedNames);

    // -------------- reordering a field---------------//
    updateFormPostIds(formPost, apiForm4_fieldEdited);
    final int newIndex = 2;
    Collections.swap(formPost.getFields(), 0, newIndex);
    Collections.swap(expectedNames, 0, newIndex);
    ApiForm apiForm5_fieldEdited = doFormEditPut(formPost, apiForm4_fieldEdited.getId());
    assertEquals("XYZ123", apiForm5_fieldEdited.getFields().get(newIndex).getName());
    assertEquals(
        "ABCDE",
        ((ApiStringFormField) apiForm5_fieldEdited.getFields().get(newIndex)).getDefaultValue());
    assertEquals(originalFieldCount - 1, apiForm5_fieldEdited.getFields().size());
    assertFieldOrder(apiForm5_fieldEdited, expectedNames);
  }

  private void assertFieldOrder(ApiForm apiFormWithAddedField, List<String> expectedNames) {
    List<String> actualNames =
        apiFormWithAddedField.getFields().stream()
            .map(ApiFormField::getName)
            .collect(Collectors.toList());
    assertTrue(CollectionUtils.isEqualCollection(actualNames, expectedNames));
  }

  private ApiForm postANewForm(FormTemplatesCommon.FormPost formPost) throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/forms", anyUser, formPost))
            .andExpect(status().isCreated())
            .andReturn();

    ApiForm apiForm = getFromJsonResponseBody(result, ApiForm.class);
    return apiForm;
  }

  private ApiForm doFormEditPut(FormTemplatesCommon.FormPost formPost, Long formId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForPutWithJSONBody(apiKey, "/forms/" + formId, anyUser, formPost))
            .andExpect(status().isOk())
            .andReturn();
    ApiForm apiForm = getFromJsonResponseBody(result, ApiForm.class);
    return apiForm;
  }

  // sets IDs of the fields to post, to match that of the returned id
  private void updateFormPostIds(
      FormTemplatesCommon.FormPost formPost, ApiForm apiFormWithAddedField) {
    for (int i = 0; i < apiFormWithAddedField.getFields().size(); i++) {
      long id = apiFormWithAddedField.getFields().get(i).getId();
      formPost.getFields().get(i).setId(id);
    }
  }

  private FormTemplatesCommon.StringFieldPost createAStringField() {
    FormTemplatesCommon.StringFieldPost stringFF = new FormTemplatesCommon.StringFieldPost();
    stringFF.setDefaultValue("some string");
    stringFF.setName("sff2");
    return stringFF;
  }
}
