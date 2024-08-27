package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class SamplesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  static final int NUM_FIELDS_IN_COMPLEX_SAMPLE = 10;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getUserSamples() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create 3 samples
    createBasicSampleForUser(anyUser);
    createComplexSampleForUser(anyUser);
    createComplexSampleForUser(anyUser);

    // no pagination
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/samples", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleSearchResult allSamples = getFromJsonResponseBody(result, ApiSampleSearchResult.class);
    assertNotNull(allSamples);
    assertEquals(3, allSamples.getTotalHits().intValue());
    assertEquals(3, allSamples.getSamples().size());
    assertEquals(1, allSamples.getLinks().size());

    // pagination - first page
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples", anyUser)
                    .param("pageSize", "1")
                    .param("pageNumber", "0"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleSearchResult paginatedSamplesFirstPage =
        getFromJsonResponseBody(result, ApiSampleSearchResult.class);
    assertEquals(3, paginatedSamplesFirstPage.getTotalHits().intValue());
    assertEquals(1, paginatedSamplesFirstPage.getSamples().size());
    assertEquals("myComplexSample", paginatedSamplesFirstPage.getSamples().get(0).getName());
    assertEquals(3, paginatedSamplesFirstPage.getLinks().size());

    // pagination - 3rd page
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/samples", anyUser)
                    .param("pageSize", "1")
                    .param("pageNumber", "2"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleSearchResult paginatedSamplesLastPage =
        getFromJsonResponseBody(result, ApiSampleSearchResult.class);
    assertEquals(3, paginatedSamplesLastPage.getTotalHits().intValue());
    assertEquals(1, paginatedSamplesLastPage.getSamples().size());
    assertEquals("mySample", paginatedSamplesLastPage.getSamples().get(0).getName());
    assertEquals(3, paginatedSamplesLastPage.getLinks().size());
  }

  @Test
  public void getSampleById() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiContainer workbench = getWorkbenchForUser(anyUser);

    // new basic sample with subsample inside a subcontainer
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(anyUser);
    // move subsample into new subcontainer
    ApiContainer topContainer = createBasicContainerForUser(anyUser);
    ApiContainer subContainer = new ApiContainer("mySubContainer", ContainerType.LIST);
    subContainer.setParentContainer(topContainer);
    subContainer = containerApiMgr.createNewApiContainer(subContainer, anyUser);
    moveSubSampleIntoListContainer(
        sample.getSubSamples().get(0).getId(), subContainer.getId(), anyUser);

    // retrieve basic sample
    MvcResult result =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, sample.getId())).andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiSample apiSample = getFromJsonResponseBody(result, ApiSample.class);
    assertNotNull(apiSample);
    assertEquals(sample.getId(), apiSample.getId());
    assertEquals(0, apiSample.getFields().size());
    ApiSubSampleInfo apiSubSample = apiSample.getSubSamples().get(0);
    assertNotNull(apiSubSample);
    assertEquals("mySubSample", apiSubSample.getName());
    assertEquals(3, apiSubSample.getParentContainers().size());
    assertEquals(subContainer.getId(), apiSubSample.getParentContainers().get(0).getId());
    assertEquals(topContainer.getId(), apiSubSample.getParentContainers().get(1).getId());
    assertEquals(workbench.getId(), apiSubSample.getParentContainers().get(2).getId());

    // new complex sample
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    result =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, complexSample.getId())).andReturn();
    assertNull(result.getResolvedException(), "uexpected: " + result.getResolvedException());

    // retrieve complex sample
    ApiSample retrievedComplexSample = getFromJsonResponseBody(result, ApiSample.class);
    assertNotNull(retrievedComplexSample);
    assertEquals(complexSample.getId(), retrievedComplexSample.getId());
    // check fields
    assertEquals(NUM_FIELDS_IN_COMPLEX_SAMPLE, retrievedComplexSample.getFields().size());
    ApiField dateField = retrievedComplexSample.getFields().get(0);
    assertEquals(ApiFieldType.NUMBER, dateField.getType());
    assertEquals(1, retrievedComplexSample.getExtraFields().size());
    // check subsample
    ApiSubSampleInfo complexSubSample = retrievedComplexSample.getSubSamples().get(0);
    assertNotNull(complexSubSample);
  }

  @Test
  public void getSampleErrorMessages() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    EcatDocumentFile galleryFile = addDocumentToGallery(anyUser);
    String apiKey = createApiKeyForuser(anyUser);

    User otherUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(otherUser);

    // not-existent
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/12345", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // authentication failure returned in preference to 404 if apikey wrong:
    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, "WRONG KEY", "/samples/" + galleryFile.getId(), anyUser))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }

  @Test
  public void createBasicSamples() throws Exception {
    Mockito.reset(auditer);

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    String simplestSampleJSON = "{\"name\": \"sample-cbs1\"}";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, simplestSampleJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sampleFromEmptyRequest =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    Long simplestSampleId = sampleFromEmptyRequest.getId();
    assertNotNull(simplestSampleId);
    assertEquals("sample-cbs1", sampleFromEmptyRequest.getName());
    assertNull(sampleFromEmptyRequest.getTemplateId());
    assertEquals(anyUser.getUsername(), sampleFromEmptyRequest.getCreatedBy());
    assertEquals(anyUser.getUsername(), sampleFromEmptyRequest.getModifiedBy());
    assertEquals(1, sampleFromEmptyRequest.getSubSamples().size());
    assertEquals(1, sampleFromEmptyRequest.getSubSamplesCount());
    verifyAuditAction(AuditAction.CREATE, 2);

    String vendorSampleJSON =
        "{ \"name\": \"My MVCIT"
            + " Sample2\",\"tags\":[{\"value\":\"aTagValue\",\"uri\":\"uriValue\",\"ontologyName\":\"ontName\",\"ontologyVersion\":1}],"
            + " \"sampleSource\":\"VENDOR_SUPPLIED\", \"subSamples\": [ { } ],  \"fields\" : [  ]"
            + " }";
    result =
        this.mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/samples", anyUser)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(vendorSampleJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sampleFromSimpleRequest =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    assertNotNull(sampleFromSimpleRequest.getId());
    assertEquals("My MVCIT Sample2", sampleFromSimpleRequest.getName());
    assertEquals(
        "aTagValue__RSP_EXTONT_URL_DELIM__uriValue__RSP_EXTONT_NAME_DELIM__ontName__RSP_EXTONT_VERSION_DELIM__1",
        sampleFromSimpleRequest.getTags().get(0).toString());
    assertNull(sampleFromSimpleRequest.getTemplateId());
    assertEquals(SampleSource.VENDOR_SUPPLIED, sampleFromSimpleRequest.getSampleSource());
    assertEquals(0, sampleFromSimpleRequest.getFields().size());
    ApiSubSampleInfo createdSubSample = sampleFromSimpleRequest.getSubSamples().get(0);

    assertEquals("My MVCIT Sample2.01", createdSubSample.getName());
    verifyAuditAction(AuditAction.CREATE, 4);

    Mockito.verifyNoMoreInteractions(auditer);
  }

  @Test
  public void createSampleErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // no name
    String json = "{}";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().isBadRequest())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "name is a required field");

    // problem with validating ApiSample model (name too long)
    String tooLongName = StringUtils.leftPad("test", 256, '*');
    json = "{ \"name\": \"" + tooLongName + "\" }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().isBadRequest())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "Name cannot be longer than");

    // wrong form id
    json = "{\"name\": \" sampleXYZ\", \"templateId\" : \"-11\" }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "No sample template with id: -11");

    //        // wrong format of form globalId
    //        json = "{\"name\": \" sampleXYZ\", \"form\" : { \"globalId\": \"11\"} }";
    //        result = this.mockMvc
    //                .perform(createBuilderForPostWithJSONBody( apiKey, "/samples", anyUser, json))
    //                .andExpect(status().is4xxClientError()).andReturn();
    //        error = getErrorFromJsonResponseBody(result, ApiError.class);
    //        assertApiErrorContainsMessage(error,"Wrong format of provided form.globalId [11]");

    // finally, a correct call
    json = String.format("{\"name\": \"sampleXYZcbd\" }");
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().isCreated())
            .andReturn();
  }

  @Test
  public void createEditSampleWithFields() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create sample, check field type is case-insensitive
    Long complexSampleTemplateId = getComplexSampleTemplate(anyUser).get().getId();
    String emptyComplexSampleFieldsJSON =
        "{ \"templateId\": \""
            + complexSampleTemplateId
            + "\",\"fields\": [ { \"content\": \"3.14\" }, {}, {},  { \"type\": \"Text\","
            + " \"content\": \"text content\" }, {}, {}, {}, {}, {}, {}], \"extraFields\" : [   {"
            + " \"name\": \"extraFieldName\", \"type\" : \"number\", \"content\": \"3.15\" } ],"
            + " \"expiryDate\":null, \"name\": \"sample1\" }";

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, emptyComplexSampleFieldsJSON))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSampleWithFullSubSamples createdSample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    assertNotNull(createdSample);
    final int textFieldIndex = 3;
    ApiField createdTextField = createdSample.getFields().get(textFieldIndex);
    assertNotNull(createdSample.getTemplateId());
    assertFalse(createdSample.isTemplate());
    assertEquals(anyUser.getUsername(), createdSample.getCreatedBy());
    assertEquals(anyUser.getUsername(), createdSample.getModifiedBy());
    assertEquals("text content", createdTextField.getContent());
    assertEquals(1, createdSample.getExtraFields().size());
    ApiExtraField createdExtraField = createdSample.getExtraFields().get(0);
    assertNotNull(createdExtraField);
    assertEquals("3.15", createdExtraField.getContent());

    // update sample tags, text field content and extra field content, also add new extra field
    String sampleUpdateJson =
        "{ \"tags\": [ {\"value\":\"api edited tags\"} ], "
            + "\"fields\": [ { \"id\": \""
            + createdTextField.getId()
            + "\", \"content\": \"updated content\" } ], "
            + "\"extraFields\": [ { \"id\": \""
            + createdExtraField.getId()
            + "\", \"content\": \"3.1415\" }, { \"newFieldRequest\": \"true\", \"content\":"
            + " \"another extra field content\",\"name\":\"newFieldName\" } ] } ";
    MvcResult editResult =
        putSampleExpectOK(anyUser, apiKey, createdSample.getId(), sampleUpdateJson);
    ApiSample editedSample = mvcUtils.getFromJsonResponseBody(editResult, ApiSample.class);

    assertNotNull(editedSample);
    assertEquals("api edited tags", editedSample.getDBStringFromTags());
    ApiField editedTextField = editedSample.getFields().get(textFieldIndex);
    assertEquals("updated content", editedTextField.getContent());
    assertEquals(2, editedSample.getExtraFields().size());
    ApiExtraField editedExtraField = editedSample.getExtraFields().get(0);
    assertEquals("3.1415", editedExtraField.getContent());
    ApiExtraField addedExtraField = editedSample.getExtraFields().get(1);
    assertEquals("another extra field content", addedExtraField.getContent());

    // delete extra field
    sampleUpdateJson =
        "{ \"extraFields\": [ { \"id\": \""
            + editedExtraField.getId()
            + "\", \"deleteFieldRequest\": \"true\" } ] } ";
    editResult = putSampleExpectOK(anyUser, apiKey, createdSample.getId(), sampleUpdateJson);
    editedSample = mvcUtils.getFromJsonResponseBody(editResult, ApiSample.class);
    assertNotNull(editedSample);
    assertEquals(1, editedSample.getExtraFields().size());
    assertEquals(addedExtraField.getContent(), editedSample.getExtraFields().get(0).getContent());

    // check sample revision history

    // check revisions list
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + createdSample.getId() + "/revisions",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(3, history.getRevisions().size());

    // check details of 1st revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/"
                        + createdSample.getId()
                        + "/revisions/"
                        + history.getRevisions().get(0).getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleRev1Full = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("sample1", sampleRev1Full.getName());
    assertTrue(sampleRev1Full.getTags().isEmpty());
    assertEquals(10, sampleRev1Full.getFields().size());
    assertEquals("text content", sampleRev1Full.getFields().get(3).getContent());
    assertEquals(1, sampleRev1Full.getExtraFields().size());

    // check details of 2nd revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/"
                        + createdSample.getId()
                        + "/revisions/"
                        + history.getRevisions().get(1).getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleRev2Full = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("api edited tags", sampleRev2Full.getDBStringFromTags());
    assertEquals("updated content", sampleRev2Full.getFields().get(3).getContent());
    assertEquals(2, sampleRev2Full.getExtraFields().size());

    // check details of 3rd revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/"
                        + createdSample.getId()
                        + "/revisions/"
                        + history.getRevisions().get(2).getRevisionId(),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleRev3Full = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals(1, sampleRev3Full.getExtraFields().size());
  }

  private Optional<Sample> getComplexSampleTemplate(User user) {
    return sampleApiMgr.getAllTemplates(user).stream()
        .filter(
            t ->
                ContentInitializerForDevRunManager.COMPLEX_SAMPLE_TEMPLATE_NAME.equals(t.getName()))
        .findAny();
  }

  @Test
  public void editSampleWithExpiryDate() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    // this has a valid expiry date set 1 year from now.
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    LocalDate expiry = complexSample.getExpiryDate();

    String correctJson = "{\"name\": \"sample2\"}";
    // it's ok to send a put request without the  expiry date,
    putSampleExpectOK(anyUser, apiKey, complexSample.getId(), correctJson);

    // ... the original remains the same
    ApiSample updatedSample = sampleApiMgr.getApiSampleById(complexSample.getId(), anyUser);
    assertNotNull(updatedSample.getExpiryDate());
    assertEquals(expiry, updatedSample.getExpiryDate());

    // it's ok to set to another valid value
    LocalDate newExpiryDate = LocalDate.now().plus(5, ChronoUnit.DAYS);
    correctJson =
        String.format("{\"name\": \"sample2\", \"expiryDate\":\"%s\"}", newExpiryDate.toString());
    putSampleExpectOK(anyUser, apiKey, complexSample.getId(), correctJson);
    updatedSample = sampleApiMgr.getApiSampleById(complexSample.getId(), anyUser);
    assertEquals(newExpiryDate, updatedSample.getExpiryDate());

    // it's ok to set a past date (rsinv-159)
    LocalDate invalidExpiryDate = LocalDate.now().minus(5, ChronoUnit.DAYS);
    String invalidExpDateJson =
        String.format(
            "{\"name\": \"sample2\", \"expiryDate\":\"%s\"}", invalidExpiryDate.toString());
    putSampleExpectOK(anyUser, apiKey, complexSample.getId(), invalidExpDateJson);

    // it's ok to set as explicitly null
    String resetToNullExpiryDate = "{\"name\": \"sample2\", \"expiryDate\":null}";
    putSampleExpectOK(anyUser, apiKey, complexSample.getId(), resetToNullExpiryDate);
    updatedSample = sampleApiMgr.getApiSampleById(complexSample.getId(), anyUser);
    assertNull(updatedSample.getExpiryDate());
  }

  private MvcResult putSampleExpectOK(User anyUser, String apiKey, Long sampleId, String sampleJson)
      throws Exception {
    return mockMvc
        .perform(
            createBuilderForPutWithJSONBody(apiKey, "/samples/" + sampleId, anyUser, sampleJson))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult putSampleExpect400(
      User anyUser, String apiKey, Long sampleId, String sampleJson) throws Exception {
    return mockMvc
        .perform(
            createBuilderForPutWithJSONBody(apiKey, "/samples/" + sampleId, anyUser, sampleJson))
        .andExpect(status().is4xxClientError())
        .andReturn();
  }

  @Test
  public void createSampleWithFieldsErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    Sample complexTemplateInfo =
        sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(
            complexSample.getTemplateId(), anyUser);

    // fields provided, but wrong number
    String json =
        String.format(
            "{\"name\": \"sample1\", \"templateId\" : \"%s\", \"fields\" : [ {}, {} ] }",
            complexTemplateInfo.getId());
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error,
        "\"fields\" array should have " + NUM_FIELDS_IN_COMPLEX_SAMPLE + " fields, but had 2");

    // incorrect new value for a field (trying update number field with text)
    String incorrectFieldContentJson =
        String.format(
            "{\"name\": \"sample1\", \"templateId\" : \"%s\", "
                + "\"fields\" : [ { \"content\": \"test\" }, {}, {}, {}, {}, {}, {}, {},{},{}] }",
            complexTemplateInfo.getId());
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, incorrectFieldContentJson))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "Invalid number");

    // extra number field with non-numeric content
    json =
        "{ \"name\": \"sample1\",\"extraFields\" : [ { \"type\" : \"number\", \"content\": \"qwer\""
            + " } ] }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "'qwer' cannot be parsed into number");

    // extra field with restricted name
    json =
        "{ \"name\": \"sample1\",\"extraFields\" : [ { \"name\": \"description\", \"type\" :"
            + " \"text\", \"content\": \"qwer\" } ] }";
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error,
        "description is a reserved field name, "
            + "please choose a name other than description/expiry date/name/source/tags");

    // finally, a correct call
    json =
        String.format(
            "{\"name\": \"sample1\", \"templateId\" : \"%s\", \"fields\" : [ { \"content\": \"23\""
                + " }, {}, {}, { \"content\": \"final call\" }, {}, {}, {}, {}, {}, {}] }",
            complexTemplateInfo.getId());
    result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, json))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples createdSample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    assertEquals("final call", createdSample.getFields().get(3).getContent());
  }

  @Test
  public void editSampleWithFieldsErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);

    // field without id provided
    String fieldWithoutIdJson =
        "{\"name\": \"sample1\", \"fields\" : [ { \"content\" : \"updated\" } ] }";

    MvcResult editResult =
        putSampleExpect400(anyUser, apiKey, complexSample.getId(), fieldWithoutIdJson);

    ApiError error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "'id' property not provided for a field");

    // field with incorrect id
    String incorrectFieldIdJson =
        "{\"name\": \"sample1\", \"fields\" : [ { \"id\":\"123\", \"content\" : \"updated\"} ] }";
    editResult = putSampleExpect400(anyUser, apiKey, complexSample.getId(), incorrectFieldIdJson);
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Field id: 123 doesn't match any of the field ids of current sample");

    // incorrect new value for a field (trying update date field with text)
    String incorrectFieldContentJson =
        "{ \"name\": \"sample1\",\"fields\" : [ { \"id\":\""
            + complexSample.getFields().get(0).getId()
            + "\", \"content\" : \"updated\"} ] }";
    editResult =
        putSampleExpect400(anyUser, apiKey, complexSample.getId(), incorrectFieldContentJson);
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "Invalid number");

    // incorrect new extra field using restricted name
    String incorrectFieldNameJson =
        "{ \"extraFields\" : [ { \"newFieldRequest\": true, "
            + "\"name\": \"source\", \"type\" : \"text\", \"content\": \"test\" } ] }";
    editResult = putSampleExpect400(anyUser, apiKey, complexSample.getId(), incorrectFieldNameJson);
    error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "source is a reserved field name");

    // finally, a correct call
    String correctJson =
        "{\"name\": \"sample1\", \"fields\" : [ { \"id\":\""
            + complexSample.getFields().get(5).getId()
            + "\", \"content\" : \"updated\"} ] }";
    editResult = putSampleExpectOK(anyUser, apiKey, complexSample.getId(), correctJson);

    // verify that correct call updated the field in db
    ApiSample updatedSample = sampleApiMgr.getApiSampleById(complexSample.getId(), anyUser);
    assertEquals("updated", updatedSample.getFields().get(5).getContent());
  }

  @Test
  public void checkExampleContentCreation() throws Exception {
    User anyUser = createAndSaveUser(getRandomName(10));
    logoutAndLoginAs(anyUser);
    initUser(anyUser, true); // initialise example templates and content
    String apiKey = createApiKeyForuser(anyUser);

    // get all samples
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/samples", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleSearchResult foundSamples =
        getFromJsonResponseBody(result, ApiSampleSearchResult.class);
    assertNotNull(foundSamples);
    assertEquals(2, foundSamples.getTotalHits().intValue());
    List<ApiSampleInfo> samples = foundSamples.getSamples();
    assertEquals(2, samples.size());
    ApiSampleInfo complexSampleInfo =
        samples.stream()
            .filter(
                s ->
                    s.getName()
                        .equals(ContentInitializerForDevRunManager.EXAMPLE_COMPLEX_SAMPLE_NAME))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("complex sample not found"));
    assertEquals("aliquot", complexSampleInfo.getSubSampleAlias().getAlias());

    // retrieve complex sample
    MvcResult complexSampleGetResult =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, complexSampleInfo.getId())).andReturn();
    ApiSample complexSample = getFromJsonResponseBody(complexSampleGetResult, ApiSample.class);
    assertNotNull(complexSample);
    assertEquals(NUM_FIELDS_IN_COMPLEX_SAMPLE, complexSample.getFields().size());
    assertEquals(1, complexSample.getExtraFields().size());
    assertEquals(1, complexSample.getSubSamples().size());
    assertEquals(1, complexSample.getSubSamplesCount());

    MvcResult complexSubSampleGetResult =
        this.mockMvc
            .perform(
                getSubSampleById(anyUser, apiKey, complexSample.getSubSamples().get(0).getId()))
            .andReturn();
    ApiSubSample complexSubSample =
        getFromJsonResponseBody(complexSubSampleGetResult, ApiSubSample.class);
    assertEquals(1, complexSubSample.getExtraFields().size());
  }

  @Test
  public void createSampleSeries() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create sample with 3 subsamples
    String emptySampleJSON2 =
        "{ \"name\": \"createSampleSeriesA\", \"newSampleSubSamplesCount\": 3, "
            + "\"quantity\": { \"numericValue\": \"30.0\", \"unitId\": \"3\" } }";

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, emptySampleJSON2))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples firstSample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    assertNotNull(firstSample);
    assertNotNull(firstSample.getId());
    assertEquals("createSampleSeriesA", firstSample.getName());
    assertEquals("30 ml", firstSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, firstSample.getSubSamples().size());
    assertEquals(3, firstSample.getSubSamplesCount());
    ApiSubSample firstSubSample = firstSample.getSubSamples().get(0);
    assertEquals("createSampleSeriesA.01", firstSubSample.getName());
    assertEquals("createSampleSeriesA.03", firstSample.getSubSamples().get(2).getName());
    assertEquals("10 ml", firstSubSample.getQuantity().toQuantityInfo().toPlainString());

    // delete one of the subsamples
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/subSamples/{id}", anyUser, firstSubSample.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // check parent sample quantity
    result = this.mockMvc.perform(getSampleById(anyUser, apiKey, firstSample.getId())).andReturn();
    ApiSample sampleWithDeletedSubSample = getFromJsonResponseBody(result, ApiSample.class);
    assertNotNull(sampleWithDeletedSubSample);
    assertEquals(2, sampleWithDeletedSubSample.getSubSamples().size());
    assertEquals(2, sampleWithDeletedSubSample.getSubSamplesCount());
    assertEquals(
        "20 ml", sampleWithDeletedSubSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void createSampleSeriesErrors() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // when providing subSamplesCount don't provide sample.subSamples array
    String simpleSampleWithSubSampleJSON =
        "{ \"name\": \"sample2\", \"newSampleSubSamplesCount\": 3, \"subSamples\": [ { } ] }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, simpleSampleWithSubSampleJSON))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "subSamples array must be empty if newSampleSubSamplesCount");

    // limit on subSamplesCount
    String tooManySubSamplesJSON = "{ \"name\": \"sample2\", \"newSampleSubSamplesCount\": 101 }";
    result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, tooManySubSamplesJSON))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "subSamplesCount supported values are 1-100, was [101]");
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void validateNameForNewSample() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(anyUser);

    // too long name warning
    String name = StringUtils.leftPad("test", 256, '*');
    MvcResult result =
        this.mockMvc
            .perform(getSampleNameValidation(anyUser, apiKey, name))
            .andExpect(status().isOk())
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(result);
    assertEquals(false, data.get("valid"));
    assertEquals("Name is too long (max 255 chars)", data.get("message"));

    // non-unique (existing for user) name warning
    name = sample.getName();
    result =
        this.mockMvc
            .perform(getSampleNameValidation(anyUser, apiKey, name))
            .andExpect(status().isOk())
            .andReturn();
    data = parseJSONObjectFromResponseStream(result);
    assertEquals(false, data.get("valid"));
    assertEquals("There is already a sample named [mySample]", data.get("message"));

    // unique name is fine
    name = sample.getName() + " #2";
    result =
        this.mockMvc
            .perform(getSampleNameValidation(anyUser, apiKey, name))
            .andExpect(status().isOk())
            .andReturn();
    data = parseJSONObjectFromResponseStream(result);
    assertEquals(true, data.get("valid"));
  }

  private MockHttpServletRequestBuilder getSampleNameValidation(
      User anyUser, String apiKey, String nameToValidate) {
    return createBuilderForGet(
            API_VERSION.ONE, apiKey, "/samples/validateNameForNewSample", anyUser)
        .param("name", nameToValidate);
  }

  @Test
  public void deleteSampleAndCheckActions() throws Exception {
    Mockito.reset(auditer);

    // create user with sample
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(anyUser);
    ApiContainer basicContainer = createBasicContainerForUser(anyUser);
    assertFalse(basicSample.getSubSamples().isEmpty());
    ApiSubSample subSample = basicSample.getSubSamples().get(0);
    assertFalse(subSample.isStoredInContainer());
    verifyAuditAction(AuditAction.CREATE, 3);

    // move subsample to container
    subSample = moveSubSampleIntoListContainer(subSample.getId(), basicContainer.getId(), anyUser);
    assertTrue(subSample.isStoredInContainer());
    verifyAuditAction(AuditAction.MOVE, 1);

    // verify subsample in container
    MvcResult retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicContainer.getId(), true))
            .andReturn();
    ApiContainer apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertNotNull(apiContainer);
    assertEquals(1, apiContainer.getContentSummary().getTotalCount());
    assertEquals(
        subSample.getGlobalId(), apiContainer.getLocations().get(0).getContent().getGlobalId());
    verifyAuditAction(AuditAction.READ, 1);

    // try deleting sample without forceDelete
    MvcResult deleteResult =
        mockMvc
            .perform(createBuilderForDelete(apiKey, "/samples/{id}", anyUser, basicSample.getId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSample sampleCannotBeDeleted = getFromJsonResponseBody(deleteResult, ApiSample.class);
    assertFalse(sampleCannotBeDeleted.getCanBeDeleted());
    assertTrue(sampleCannotBeDeleted.getSubSamples().get(0).isStoredInContainer());
    verifyAuditAction(AuditAction.DELETE, 0);

    // delete sample with forceDelete=true
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/samples/{id}", anyUser, basicSample.getId())
                .param("forceDelete", "true"))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    // verify audit trail deletion call for sample and subsample
    verifyAuditAction(AuditAction.DELETE, 2);

    // sample can be retrieved
    MvcResult result =
        this.mockMvc.perform(getSampleById(anyUser, apiKey, basicSample.getId())).andReturn();
    ApiSample apiSample = getFromJsonResponseBody(result, ApiSample.class);
    assertNotNull(apiSample);
    assertTrue(apiSample.isDeleted());
    verifyAuditAction(AuditAction.READ, 2);

    // sample cannot be updated
    String updateJson = "{ \"tags\": [ {\"value\":\"api edited tags\"} ] }";
    MvcResult editResult = putSampleExpect400(anyUser, apiKey, basicSample.getId(), updateJson);
    ApiError error = getErrorFromJsonResponseBody(editResult, ApiError.class);
    assertApiErrorContainsMessage(error, "is deleted");

    // subsample no longer in the container
    retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, basicContainer.getId(), true))
            .andReturn();
    apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertNotNull(apiContainer);
    assertEquals(0, apiContainer.getContentSummary().getTotalCount());
    verifyAuditAction(AuditAction.READ, 3);

    // restore the sample
    result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + basicSample.getId() + "/restore", anyUser, updateJson))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    apiSample = getFromJsonResponseBody(result, ApiSample.class);
    assertNotNull(apiSample);
    assertFalse(apiSample.isDeleted());
    // subsample that was active during sample deletion is also restored
    assertEquals(1, apiSample.getSubSamples().size());
    assertFalse(apiSample.getSubSamples().get(0).isDeleted());
    verifyAuditAction(AuditAction.RESTORE, 2);

    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void createSampleWithImage() throws Exception {
    Mockito.reset(auditer);
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    String post = "{ \"name\": \"Simple Sample\", \"newBase64Image\":\"" + BASE_64 + "\" }";
    MvcResult result =
        this.mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, post))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples sample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    Sample dbSample = sampleApiMgr.assertUserCanReadSample(sample.getId(), anyUser);
    assertNotNull(dbSample.getImageFileProperty());
    assertNotNull(dbSample.getThumbnailFileProperty());
    // 2 x image, self and icon
    assertEquals(4, sample.getLinks().size());
  }

  @Test
  public void duplicate() throws Exception {
    Mockito.reset(auditer);

    // create user with sample
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    verifyAuditAction(AuditAction.CREATE, 2);

    // copy sample
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format("/samples/%d/actions/duplicate", complexSample.getId()),
                    anyUser))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples copy =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    assertNotNull(copy.getId());

    // verify audit trail create call after copy
    verifyAuditAction(AuditAction.CREATE, 3);
    verifyNoMoreInteractions(auditer);
  }

  @Test
  public void sampleRevisionHistory() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(anyUser);

    String correctJson = "{\"name\": \"updated sample\"}";
    putSampleExpectOK(anyUser, apiKey, sample.getId(), correctJson);

    // check revision history
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/samples/" + sample.getId() + "/revisions", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryRecordRevisionList history =
        getFromJsonResponseBody(result, ApiInventoryRecordRevisionList.class);
    assertEquals(2, history.getRevisions().size());

    Long firstRevisionId = history.getRevisions().get(0).getRevisionId();
    ApiSampleInfo sampleRev1 = (ApiSampleInfo) history.getRevisions().get(0).getRecord();
    assertEquals("myComplexSample", sampleRev1.getName());
    assertEquals(1, sampleRev1.getSubSamplesCount());
    assertEquals(4, sampleRev1.getLinks().size());
    assertTrue(
        sampleRev1
            .getLinkOfType(ApiLinkItem.SELF_REL)
            .get()
            .getLink()
            .endsWith("/revisions/" + firstRevisionId));

    ApiSampleInfo sampleRev2 = (ApiSampleInfo) history.getRevisions().get(1).getRecord();
    assertEquals("updated sample", sampleRev2.getName());
    assertEquals(1, sampleRev2.getSubSamplesCount());

    // check details of first revision
    result =
        this.mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/samples/" + sample.getId() + "/revisions/" + firstRevisionId,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample sampleRev1Full = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("myComplexSample", sampleRev1Full.getName());
    assertEquals(10, sampleRev1Full.getFields().size());
    assertEquals(1, sampleRev1Full.getExtraFields().size());
    assertEquals(1, sampleRev1Full.getSubSamples().size());
    assertEquals(1, sampleRev1Full.getSubSamplesCount());
    assertEquals("mySubSample", sampleRev1Full.getSubSamples().get(0).getName());
    assertEquals(4, sampleRev1Full.getLinks().size());
    assertTrue(
        sampleRev1Full
            .getLinkOfType(ApiLinkItem.SELF_REL)
            .get()
            .getLink()
            .endsWith("/revisions/" + firstRevisionId));
  }

  @Test
  public void changeSampleOwner() throws Exception {

    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // create basic sample
    String basicSampleFieldsJSON = "{ \"name\": \"sample1\" }";
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, basicSampleFieldsJSON))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples createdSample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    assertNotNull(createdSample);
    assertEquals(anyUser.getUsername(), createdSample.getOwner().getUsername());

    // create another user
    User anotherUser = doCreateAndInitUser("anotherUser");

    // sample owner transfers sample to another user
    String sampleUpdateJson =
        "{ \"owner\": { \"username\": \"" + anotherUser.getUsername() + "\" } } ";
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/samples/" + createdSample.getId() + "/actions/changeOwner",
                    anyUser,
                    sampleUpdateJson))
            .andReturn();
    assertNull(editResult.getResolvedException());
    ApiSample editedSample = mvcUtils.getFromJsonResponseBody(editResult, ApiSample.class);
    assertNotNull(editedSample);
    assertEquals(anotherUser.getUsername(), editedSample.getOwner().getUsername());
  }

  @Test
  public void createSampleFromTemplateWithValidTimeIsAccepted() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    Long sampleTemplateId = getComplexSampleTemplate(anyUser).get().getId();
    String validTime = "10:15";
    String sampleWithExtraFieldsJSON =
        makeJsonSampleWithIdAndTime(sampleTemplateId.toString(), validTime);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, sampleWithExtraFieldsJSON))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSampleWithFullSubSamples createdSample =
        mvcUtils.getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    ApiSampleField timeField =
        createdSample.getFields().stream()
            .filter(f -> f.getType().equals(ApiFieldType.TIME))
            .findFirst()
            .get();
    assertEquals(validTime, timeField.getContent());
  }

  @Test
  public void createSampleFromTemplateWithInvalidTimeIsRejected() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    Long sampleTemplateId = getComplexSampleTemplate(anyUser).get().getId();
    String invalidTime = "9:15"; // time format should be 24 hours with 4 digits e.g. 09:15
    String sampleWithExtraFieldsJSON =
        makeJsonSampleWithIdAndTime(sampleTemplateId.toString(), invalidTime);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", anyUser, sampleWithExtraFieldsJSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    String responseText = result.getResponse().getContentAsString();
    String expectedErrorMessage = "9:15 is an invalid 24hour time format. Valid format is 00:00.";
    assertTrue(responseText.contains(expectedErrorMessage));
  }

  private String makeJsonSampleWithIdAndTime(String id, String time) {
    return "{ \"templateId\": \""
        + id
        + "\",\"fields\": [ { \"content\": \"3.14\" }, {}, {},  { \"type\": \"Text\","
        + " \"content\": \"text content\" }, {}, {}, {}, {\"type\": \"time\", \"content\": \""
        + time
        + "\"}, {}, {}],"
        + " \"expiryDate\":null, \"name\": \"sample1\" }";
  }
}
