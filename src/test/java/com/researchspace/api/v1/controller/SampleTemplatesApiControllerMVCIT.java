package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.SamplesApiControllerMVCIT.NUM_FIELDS_IN_COMPLEX_SAMPLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.*;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.jsonserialisers.LocalDateDeserialiser;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class SampleTemplatesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  User anyUser, otherUser;
  String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createApiKeyForuser(anyUser);
  }

  private ApiSampleTemplatePost createValidSampleTemplatePostNoFields() {
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("myNewTemplate");
    sampleTemplatePost.setApiTagInfo("tag1,tag2");
    sampleTemplatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    sampleTemplatePost.setSampleSource(SampleSource.LAB_CREATED);
    return sampleTemplatePost;
  }

  @Test
  public void testListSampleTemplates() throws Exception {
    ApiSampleTemplateSearchResult searchHits = listAllSampleTemplates();
    int allTemplatesCount = searchHits.getTotalHits().intValue();
    assertTrue(allTemplatesCount >= 2);
    assertEquals(allTemplatesCount, searchHits.getTemplates().size());
    assertEquals(1, searchHits.getLinks().size());

    Optional<ApiSampleTemplateInfo> complexTemplateInfoOpt =
        searchHits.getTemplates().stream()
            .filter(
                st ->
                    ContentInitializerForDevRunManager.COMPLEX_SAMPLE_TEMPLATE_NAME.equals(
                        st.getName()))
            .findFirst();
    assertTrue(complexTemplateInfoOpt.isPresent());
    ApiSampleTemplateInfo complexTemplateInfo = complexTemplateInfoOpt.get();
    assertEquals("aliquot", complexTemplateInfo.getSubSampleAlias().getAlias());
    assertNotNull(complexTemplateInfo.getModifiedByFullName());
    assertEquals(4, complexTemplateInfo.getLinks().size());

    // check template image and icon linked correctly
    String iconLink = complexTemplateInfo.getLinkOfType(ApiLinkItem.ICON_REL).get().getLink();
    assertTrue(iconLink.contains("/icon/"), "icon fragment not found in: " + iconLink);
    String imageLink = complexTemplateInfo.getLinkOfType(ApiLinkItem.IMAGE_REL).get().getLink();
    assertTrue(imageLink.contains("/image/"), "image fragment not found in: " + imageLink);

    ApiSample sampleTemplate = retrieveSampleTemplate(complexTemplateInfo.getId());
    assertNotNull(sampleTemplate);
    assertEquals(NUM_FIELDS_IN_COMPLEX_SAMPLE, sampleTemplate.getFields().size());

    // list with pagination
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/sampleTemplates", anyUser)
                    .param("pageSize", "1"))
            .andExpect(status().isOk())
            .andReturn();
    searchHits = getFromJsonResponseBody(result, ApiSampleTemplateSearchResult.class);
    //	List<ApiSampleTemplateInfo>templates =
    // searchHits.getTemplates().stream().filter(f->f.isTemplate()).collect(Collectors.toList());
    assertEquals(allTemplatesCount, searchHits.getTotalHits().intValue());
    assertEquals(1, searchHits.getTemplates().size());
    assertTrue(searchHits.getLinks().size() > 1);
  }

  @Test
  public void createListDeleteSampleTemplateRoundTrip() throws Exception {
    ApiSampleTemplateSearchResult searchHits = listAllSampleTemplates();
    int initialCount = searchHits.getTotalHits().intValue();

    // create
    ApiSampleTemplatePost templateNoFieldsPost = createValidSampleTemplatePostNoFields();
    ApiSample savedTemplate = postValidSampleTemplate(templateNoFieldsPost);
    assertTrue(savedTemplate.isTemplate());
    assertNotNull(savedTemplate.getId());
    assertEquals(GlobalIdPrefix.IT.name(), savedTemplate.getGlobalId().substring(0, 2));
    // check if listed after creation
    assertEquals(initialCount + 1, listAllSampleTemplates().getTotalHits().intValue());

    // delete
    mockMvc
        .perform(
            createBuilderForDelete(
                apiKey, "/sampleTemplates/{templateId}", anyUser, savedTemplate.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    // check that no longer listed
    assertEquals(initialCount, listAllSampleTemplates().getTotalHits().intValue());

    // restore
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/sampleTemplates/" + savedTemplate.getId() + "/restore", anyUser, null))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    // template should be listed again
    assertEquals(initialCount + 1, listAllSampleTemplates().getTotalHits().intValue());
  }

  @Test
  public void checkTemplatePostPutValidation() throws Exception {
    // create request
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("test template");
    ApiSubSampleAlias tooLongAlias =
        new ApiSubSampleAlias(StringUtils.repeat("toolongsingular", 5), " p ");
    templatePost.setSubSampleAlias(tooLongAlias);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals("Errors detected : 2", error.getMessage());
    assertEquals(
        "subSampleAlias.alias: subSampleAlias.alias cannot be greater than 30 characters.",
        error.getErrors().get(0));
    assertEquals(
        "subSampleAlias.plural: subSampleAlias.plural cannot be less than 2 characters.",
        error.getErrors().get(1));

    // fix problematic field
    templatePost.setSubSampleAlias(new ApiSubSampleAlias("ok", "oks"));
    result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSampleTemplate createdTemplate = getFromJsonResponseBody(result, ApiSampleTemplate.class);
    assertEquals("ok", createdTemplate.getSubSampleAlias().getAlias());

    // update request
    ApiSampleTemplate templateUpdate = new ApiSampleTemplate();
    templateUpdate.setName("updated template");
    ApiSubSampleAlias tooLongPluralAlias =
        new ApiSubSampleAlias("\t\t\t", StringUtils.repeat("toolongplural", 5));
    templateUpdate.setSubSampleAlias(tooLongPluralAlias);

    String updateUrl = "/sampleTemplates/" + createdTemplate.getId();
    result =
        mockMvc
            .perform(createBuilderForPutWithJSONBody(apiKey, updateUrl, anyUser, templateUpdate))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals("Errors detected : 2", error.getMessage());
    assertEquals(
        "subSampleAlias.alias: subSampleAlias.alias cannot be less than 2 characters.",
        error.getErrors().get(0));
    assertEquals(
        "subSampleAlias.plural: subSampleAlias.plural cannot be greater than 30 characters.",
        error.getErrors().get(1));

    // fix problematic field
    templateUpdate.setSubSampleAlias(new ApiSubSampleAlias("ok2", "ok2s"));
    result =
        mockMvc
            .perform(createBuilderForPutWithJSONBody(apiKey, updateUrl, anyUser, templateUpdate))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ApiSampleTemplate updatedTemplate = getFromJsonResponseBody(result, ApiSampleTemplate.class);
    assertEquals("ok2s", updatedTemplate.getSubSampleAlias().getPlural());
  }

  @Test
  public void postSampleTemplateYaml() throws Exception {
    String yamlString =
        FileUtils.readFileToString(
            RSpaceTestUtils.getResource("enzyme-sample-template.yaml"), "UTF-8");

    MvcResult result =
        mockMvc
            .perform(
                post(createUrl(API_VERSION.ONE, "/sampleTemplates"))
                    .principal(createPrincipal(anyUser))
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .header("apiKey", apiKey)
                    .content(yamlString))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleTemplate sampleTemplate = getFromJsonResponseBody(result, ApiSampleTemplate.class);
    assertEquals("Restriction Enzyme", sampleTemplate.getName());
    assertEquals(5, sampleTemplate.getFields().size());
    ApiSampleField firstField = sampleTemplate.getFields().get(0);
    assertEquals("Recognition sequence length", firstField.getName());
    assertEquals(List.of("6"), firstField.getSelectedOptions());
  }

  @Test
  public void saveAndGetIcon() throws Exception {
    ApiSampleTemplatePost templateNoFieldsPost = createValidSampleTemplatePostNoFields();
    ApiSample savedTemplate = postValidSampleTemplate(templateNoFieldsPost);
    MockMultipartFile mf =
        new MockMultipartFile(
            "file", "Picture1.png", "image/png", getTestResourceFileStream("Picture1.png"));
    MvcResult result =
        mockMvc
            .perform(
                multipart(
                        createUrl(
                            API_VERSION.ONE, "/sampleTemplates/" + savedTemplate.getId() + "/icon"))
                    .file(mf)
                    .header("apiKey", apiKey))
            .andReturn();
    ApiSampleInfo sampleTemplate = getFromJsonResponseBody(result, ApiSampleInfo.class);
    assertTrue(sampleTemplate.getIconId() > 0);

    MvcResult iconImageResp =
        mockMvc
            .perform(
                createBuilderForGet2(
                    API_VERSION.ONE,
                    apiKey,
                    "/sampleTemplates/{id}/icon/{iconId}",
                    () -> anyUser::getUsername,
                    sampleTemplate.getId(),
                    sampleTemplate.getIconId()))
            .andExpect(status().isOk())
            .andReturn();
    int thumbnailSize = iconImageResp.getResponse().getContentAsByteArray().length;
    assertTrue(thumbnailSize == 1377 || thumbnailSize == 1410); // java 8 and 11 values
  }

  @Test
  public void createUpdateSampleTemplateWithFields() throws Exception {
    ApiSampleTemplateSearchResult searchHits = listAllSampleTemplates();
    int initialCount = searchHits.getTotalHits().intValue();
    ApiSampleTemplatePost templatePost = createValidSampleTemplatePostNoFields();
    // create 10 fields, 1 of each type
    ApiSampleField text = createBasicApiSampleField("text", ApiFieldType.TEXT, "text value");
    ApiSampleField string =
        createBasicApiSampleField("string", ApiFieldType.STRING, "string value");
    ApiSampleField date = createBasicApiSampleField("date", ApiFieldType.DATE, "2020-10-31");
    ApiSampleField time = createBasicApiSampleField("time", ApiFieldType.TIME, "23:45:12");
    ApiSampleField number = createBasicApiSampleField("number", ApiFieldType.NUMBER, "112.34");

    ApiSampleField choice =
        createBasicApiSampleOptionsField("choice", ApiFieldType.CHOICE, List.of("1", "2"));
    ApiInventoryFieldDef def = new ApiInventoryFieldDef();
    def.setOptions(List.of("1", "2", "3"));
    choice.setDefinition(def);

    ApiSampleField radio =
        createBasicApiSampleOptionsField("radio", ApiFieldType.RADIO, List.of("1"));
    ApiInventoryFieldDef def2 = new ApiInventoryFieldDef();
    def2.setOptions(List.of("1", "2", "3"));
    radio.setDefinition(def2);

    ApiSampleField attach = createBasicApiSampleField("attach", ApiFieldType.ATTACHMENT, "attach");
    ApiSampleField ref = createBasicApiSampleField("ref", ApiFieldType.REFERENCE, "ref value");
    ApiSampleField URI =
        createBasicApiSampleField("uri", ApiFieldType.URI, "https://somewhere.com");

    templatePost.setFields(
        TransformerUtils.toList(text, string, date, time, number, choice, radio, attach, ref, URI));
    log.info(JacksonUtil.toJson(templatePost));
    ApiSampleTemplate savedTemplate = postValidSampleTemplate(templatePost);
    assertTrue(savedTemplate.isTemplate());
    assertEquals(GlobalIdPrefix.IT.name(), savedTemplate.getGlobalId().substring(0, 2));
    assertEquals(initialCount + 1, listAllSampleTemplates().getTotalHits().intValue());
    assertEquals(10, savedTemplate.getFields().size());
    assertEquals(
        2, savedTemplate.getFields().stream().filter(f -> f.getDefinition() != null).count());
    ApiField savedTextField = savedTemplate.getFields().get(0);
    assertEquals("text", savedTextField.getName());
    ApiField savedChoiceField = savedTemplate.getFields().get(5);
    assertEquals("choice", savedChoiceField.getName());

    // default
    assertEquals(
        SubSampleName.SUBSAMPLE.getDisplayName(), savedTemplate.getSubSampleAlias().getAlias());
    // assertLinks are made ok
    assertTrue(
        savedTemplate.getLinks().stream()
            .anyMatch(ali -> ali.getRel().equals(ApiLinkItem.SELF_REL)));
    assertTrue(
        savedTemplate.getLinks().stream()
            .filter(ali -> ali.getRel().equals(ApiLinkItem.SELF_REL))
            .findFirst()
            .get()
            .getLink()
            .contains(BaseApiInventoryController.SAMPLE_TEMPLATES_ENDPOINT));

    // update template: add one field, modify other, remove third
    String templateUpdateJson =
        "{ \"name\": \"updatedTemplateName\", \"fields\": [ "
            + "  { \"id\": \""
            + savedTextField.getId()
            + "\", \"deleteFieldRequest\": \"true\" },"
            + "  { \"id\": \""
            + savedChoiceField.getId()
            + "\", \"definition\": { \"options\": [\"1\",\"2\",\"3\",\"4\"] } },  {"
            + " \"newFieldRequest\": \"true\", \"type\": \"NUMBER\", \"content\":"
            + " \"-3.14\",\"name\":\"new number\" } ] } ";
    ApiSample updatedTemplate = putValidSampleTemplate(templateUpdateJson, savedTemplate.getId());
    assertNotNull(updatedTemplate);
    assertEquals("updatedTemplateName", updatedTemplate.getName());
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(10, updatedTemplate.getFields().size());
    assertEquals(
        "string", updatedTemplate.getFields().get(0).getName()); // string field is now first
    assertEquals(
        List.of("1", "2", "3", "4"),
        updatedTemplate.getFields().get(4).getDefinition().getOptions()); // choice is updated
    assertEquals("new number", updatedTemplate.getFields().get(9).getName()); // new number is last

    // create sample from updated template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from updated template");
    apiSample.setTemplateId(updatedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample = sampleApiMgr.createNewApiSample(apiSample, anyUser);
    assertNotNull(createdSample);
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), anyUser);
    assertEquals(updatedTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(updatedTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(2, retrievedSample.getTemplateVersion());
    assertEquals(10, retrievedSample.getFields().size());
    assertEquals("new number", retrievedSample.getFields().get(9).getName());
  }

  @Test
  public void updateSampleTemplateChoiceAndRadioFieldOptions() throws Exception {

    // prepare new template
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template with radio and choice");
    // add radio field
    ApiSampleField radioField =
        createBasicApiSampleOptionsField("my radio", ApiFieldType.RADIO, List.of("r2"));
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("r1", "r2", "r3"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);
    // add choice field
    ApiSampleField choiceField =
        createBasicApiSampleOptionsField("my choice", ApiFieldType.CHOICE, List.of("c1", "c2"));
    ApiInventoryFieldDef def = new ApiInventoryFieldDef(List.of("c1", "c2", "c3"), true);
    choiceField.setDefinition(def);
    sampleTemplatePost.getFields().add(choiceField);

    // create template
    ApiSample createdTemplate = postValidSampleTemplate(sampleTemplatePost);
    assertEquals(2, createdTemplate.getFields().size());

    // retrieve the template
    ApiSampleTemplate retrievedTemplate = retrieveSampleTemplate(createdTemplate.getId());
    assertEquals("IT" + createdTemplate.getId(), retrievedTemplate.getGlobalId());
    assertFalse(retrievedTemplate.isHistoricalVersion());
    assertEquals(2, retrievedTemplate.getFields().size());
    ApiSampleField retrievedTemplateRadioField = retrievedTemplate.getFields().get(0);
    assertEquals("my radio", retrievedTemplateRadioField.getName());
    assertEquals(List.of("r2"), retrievedTemplateRadioField.getSelectedOptions());
    ApiSampleField retrievedTemplateChoiceField = retrievedTemplate.getFields().get(1);
    assertEquals("my choice", retrievedTemplateChoiceField.getName());
    assertEquals(List.of("c1", "c2"), retrievedTemplateChoiceField.getSelectedOptions());

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(retrievedTemplate.getId());

    ApiSampleWithFullSubSamples createdSample = sampleApiMgr.createNewApiSample(apiSample, anyUser);
    assertNotNull(createdSample);
    // retrieve the sample - field definition should come from template
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), anyUser);
    assertEquals(retrievedTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(2, retrievedSample.getFields().size());
    assertEquals("my radio", retrievedSample.getFields().get(0).getName());
    assertEquals(null, retrievedSample.getFields().get(0).getContent());
    assertEquals(List.of("r2"), retrievedSample.getFields().get(0).getSelectedOptions());
    assertEquals("my choice", retrievedSample.getFields().get(1).getName());
    assertEquals(null, retrievedSample.getFields().get(1).getContent());
    assertEquals(List.of("c1", "c2"), retrievedSample.getFields().get(1).getSelectedOptions());

    // create another sample, but with empty fields
    ApiSampleWithFullSubSamples anotherApiSample =
        new ApiSampleWithFullSubSamples("another sample from template v1");
    anotherApiSample.setTemplateId(retrievedTemplate.getId());
    ApiSampleField emptyField = new ApiSampleField();
    emptyField.setContent("");
    anotherApiSample.setFields(List.of(emptyField, emptyField));
    ApiSampleWithFullSubSamples anotherCreatedSample =
        sampleApiMgr.createNewApiSample(anotherApiSample, anyUser);
    assertNotNull(anotherCreatedSample);
    assertEquals(2, anotherCreatedSample.getFields().size());
    assertEquals("my radio", anotherCreatedSample.getFields().get(0).getName());
    assertEquals(null, anotherCreatedSample.getFields().get(0).getContent());

    // prepare template update request
    ApiSample templateUpdates = new ApiSample();
    templateUpdates.setId(retrievedTemplate.getId());
    // add a new option to radio field, and set it as a default
    ApiSampleField radioFieldUpdates = new ApiSampleField();
    radioFieldUpdates.setId(retrievedTemplateRadioField.getId());
    radioFieldUpdates.setName("updated radio");
    ApiInventoryFieldDef updatedRadioDef =
        new ApiInventoryFieldDef(List.of("r2", "r3", "r4"), false);
    radioFieldUpdates.setDefinition(updatedRadioDef);
    radioFieldUpdates.setSelectedOptions(List.of("r4"));
    templateUpdates.getFields().add(radioFieldUpdates);
    // add a new option to choice field, and set it as a default
    ApiSampleField choiceFieldUpdates = new ApiSampleField();
    choiceFieldUpdates.setId(retrievedTemplateChoiceField.getId());
    choiceFieldUpdates.setName("updated choice");
    ApiInventoryFieldDef updatedChoiceDef =
        new ApiInventoryFieldDef(List.of("c2", "c3", "c4"), false);
    choiceFieldUpdates.setDefinition(updatedChoiceDef);
    choiceFieldUpdates.setSelectedOptions(List.of("c3", "c4"));
    templateUpdates.getFields().add(choiceFieldUpdates);

    // update the template
    String templateUpdateJson = JacksonUtil.toJson(templateUpdates);
    ApiSample updatedTemplate = putValidSampleTemplate(templateUpdateJson, createdTemplate.getId());
    assertNotNull(updatedTemplate);
    assertEquals("IT" + createdTemplate.getId(), updatedTemplate.getGlobalId());
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(2, updatedTemplate.getFields().size());
    retrievedTemplateRadioField = updatedTemplate.getFields().get(0);
    assertEquals("updated radio", retrievedTemplateRadioField.getName());
    retrievedTemplateChoiceField = updatedTemplate.getFields().get(1);
    assertEquals("updated choice", retrievedTemplateChoiceField.getName());

    // first sample still holds the previous field definitions
    retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), anyUser);
    assertEquals(retrievedTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(1, retrievedSample.getTemplateVersion());
    assertEquals(2, retrievedSample.getFields().size());
    assertEquals("my radio", retrievedSample.getFields().get(0).getName());
    assertEquals(null, retrievedSample.getFields().get(0).getContent());
    assertEquals(List.of("r2"), retrievedSample.getFields().get(0).getSelectedOptions());
    assertEquals(
        List.of("r1", "r2", "r3"), retrievedSample.getFields().get(0).getDefinition().getOptions());
    assertEquals("my choice", retrievedSample.getFields().get(1).getName());
    assertEquals(null, retrievedSample.getFields().get(1).getContent());
    assertEquals(List.of("c1", "c2"), retrievedSample.getFields().get(1).getSelectedOptions());
    assertEquals(
        List.of("c1", "c2", "c3"), retrievedSample.getFields().get(1).getDefinition().getOptions());

    // retrieve first version of the template from /versions/ endpoint
    ApiSampleTemplate retrievedVersion1 =
        retrieveSampleTemplateVersion(retrievedTemplate.getId(), 1L);
    assertEquals("IT" + createdTemplate.getId() + "v1", retrievedVersion1.getGlobalId());
    assertTrue(retrievedVersion1.isHistoricalVersion());
    assertEquals(2, retrievedVersion1.getFields().size());
    retrievedTemplateRadioField = retrievedVersion1.getFields().get(0);
    assertEquals("my radio", retrievedTemplateRadioField.getName());
    assertEquals(
        List.of("r1", "r2", "r3"), retrievedTemplateRadioField.getDefinition().getOptions());

    // retrieve second (current) version of the template from /versions/ endpoint
    ApiSampleTemplate retrievedVersion2 =
        retrieveSampleTemplateVersion(retrievedTemplate.getId(), 2L);
    assertEquals("IT" + createdTemplate.getId(), retrievedVersion2.getGlobalId());
    assertFalse(retrievedVersion2.isHistoricalVersion());
    assertEquals(2, retrievedVersion2.getFields().size());
    retrievedTemplateRadioField = retrievedVersion2.getFields().get(0);
    assertEquals("updated radio", retrievedTemplateRadioField.getName());
    assertEquals(null, retrievedTemplateRadioField.getContent());
    assertEquals(List.of("r4"), retrievedTemplateRadioField.getSelectedOptions());
    assertEquals(
        List.of("r2", "r3", "r4"), retrievedTemplateRadioField.getDefinition().getOptions());

    // try updating 1st sample to latest template definition
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format(
                        "/samples/%d/actions/updateToLatestTemplateVersion", createdSample.getId()),
                    anyUser))
            .andExpect(status().is5xxServerError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(
        "Field [updated choice] value [[\"c1\",\"c2\"]] is invalid according to latest template"
            + " field definition",
        error.getMessage());

    // try updating 2nd sample to latest template definition
    result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format(
                        "/samples/%d/actions/updateToLatestTemplateVersion",
                        anotherCreatedSample.getId()),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiSample updatedSample = getFromJsonResponseBody(result, ApiSample.class);
    assertEquals("another sample from template v1", updatedSample.getName());
    assertEquals("updated radio", updatedSample.getFields().get(0).getName());
  }

  @Test
  public void checkErrorsOnBulkUpdateToLatestTemplate() throws Exception {

    // prepare new template with radio & text field
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template v1");
    // add radio field
    ApiSampleField radioField =
        createBasicApiSampleOptionsField("my radio", ApiFieldType.RADIO, List.of("r1"));
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("r1", "r2", "r3"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);
    // add text field
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("my text", ApiFieldType.TEXT, "default text"));

    // create template
    ApiSample createdTemplate = postValidSampleTemplate(sampleTemplatePost);
    assertEquals(2, createdTemplate.getFields().size());

    // create a few samples from that template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample1 with r1 selected");
    apiSample.setTemplateId(createdTemplate.getId());
    // 1st is default sample, based on default template fields
    sampleApiMgr.createNewApiSample(apiSample, anyUser);
    // 2nd have a different radio option (that'll be valid after update) and some text
    apiSample.setName("sample2 with r2 selected and text content");
    ApiSampleField myRadioField = new ApiSampleField();
    myRadioField.setContent("r2");
    ApiSampleField myTextField = new ApiSampleField();
    myTextField.setContent("text");
    apiSample.setFields(List.of(myRadioField, myTextField));
    sampleApiMgr.createNewApiSample(apiSample, anyUser);
    // 3rd have a different radio option and empty text field (this one should update fine)
    apiSample.setName("sample3 with r2 selected and no text content");
    apiSample.setFields(List.of(myRadioField, new ApiSampleField()));
    sampleApiMgr.createNewApiSample(apiSample, anyUser);
    // 4th is a the same as 3rd, will use for edit lock testing
    apiSample.setName("sample4 (locked)");
    ApiSampleWithFullSubSamples sample4 = sampleApiMgr.createNewApiSample(apiSample, anyUser);

    // let's lock last sample for other user (using lock tracker directly so group setup can be
    // omitted)
    // that should cause sample4 to fail on update attempt
    User otherUser = createInitAndLoginAnyUser();
    invLockTracker.attemptToLockForEdit(sample4.getGlobalId(), otherUser);
    logoutAndLoginAs(anyUser);

    // prepare template update request: change options in radio field & delete text field
    String templateUpdateJson =
        "{\"fields\":[\n"
            + "{\"id\": "
            + createdTemplate.getFields().get(0).getId()
            + ", \"name\":\"updated radio\","
            + "\"definition\":{\"options\": [\"r2\",\"r3\",\"r4\"] },\"content\": \"r2\"}, \n"
            + "{\"id\": "
            + createdTemplate.getFields().get(1).getId()
            + ", \"deleteFieldRequest\": true} ] }";

    // update the template
    ApiSample updatedTemplate = putValidSampleTemplate(templateUpdateJson, createdTemplate.getId());
    assertNotNull(updatedTemplate);
    assertEquals("IT" + createdTemplate.getId(), updatedTemplate.getGlobalId());
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(1, updatedTemplate.getFields().size());
    ApiField retrievedTemplateRadioField = updatedTemplate.getFields().get(0);
    assertEquals("updated radio", retrievedTemplateRadioField.getName());

    // try updating all samples taken from the template to latest definition
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format(
                        "/sampleTemplates/%d/actions/updateSamplesToLatestTemplateVersion",
                        createdTemplate.getId()),
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInventoryBulkOperationResult bulkUpdateResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, bulkUpdateResult.getStatus());
    assertEquals(2, bulkUpdateResult.getSuccessCount());
    assertEquals(2, bulkUpdateResult.getErrorCount());
    // error from attempt to update the 1st sample - radio option no longer available
    assertEquals(
        "Field [updated radio] value [r1] is invalid according to latest template field definition",
        bulkUpdateResult.getResults().get(0).getError().getErrors().get(0));
    // success from attempt to update 2nd and 3rd sample
    assertEquals(
        "sample2 with r2 selected and text content",
        bulkUpdateResult.getResults().get(1).getRecord().getName());
    assertEquals(
        "sample3 with r2 selected and no text content",
        bulkUpdateResult.getResults().get(2).getRecord().getName());
    // error from attempt to update the 4th sample - edit lock
    assertEquals(
        "Item is currently edited by another user (" + otherUser.getUsername() + ")",
        bulkUpdateResult.getResults().get(3).getError().getErrors().get(0));
  }

  @Test
  public void changeSampleTemplateOwner() throws Exception {

    // create template for anyUser
    ApiSampleTemplatePost templateNoFieldsPost = createValidSampleTemplatePostNoFields();
    ApiSampleTemplate savedTemplate = postValidSampleTemplate(templateNoFieldsPost);
    assertTrue(savedTemplate.isTemplate());
    assertNotNull(savedTemplate.getId());

    // create another user
    User anotherUser = doCreateAndInitUser("anotherUser");

    // template owner transfers sample to another user
    String templateUpdateJson =
        "{ \"owner\": { \"username\": \"" + anotherUser.getUsername() + "\" } } ";
    MvcResult editResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/sampleTemplates/" + savedTemplate.getId() + "/actions/changeOwner",
                    anyUser,
                    templateUpdateJson))
            .andReturn();
    assertNull(editResult.getResolvedException());
    ApiSampleTemplate editedTemplate =
        mvcUtils.getFromJsonResponseBody(editResult, ApiSampleTemplate.class);
    assertNotNull(editedTemplate);
    assertEquals(anotherUser.getUsername(), editedTemplate.getOwner().getUsername());
  }

  @Test
  public void checkDefaultTemplateFieldsOnCreatedSample() throws Exception {

    // create template for anyUser
    ApiSampleTemplatePost templateNoFieldsPost = createValidSampleTemplatePostNoFields();
    templateNoFieldsPost.setExpiryDate(LocalDate.of(2022, 02, 25));

    ApiSampleTemplate savedTemplate = postValidSampleTemplate(templateNoFieldsPost);
    assertTrue(savedTemplate.isTemplate());
    assertNotNull(savedTemplate.getId());
    assertNotNull(savedTemplate.getExpiryDate());

    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("default sample from template");
    apiSample.setTemplateId(savedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample = sampleApiMgr.createNewApiSample(apiSample, anyUser);
    assertNotNull(createdSample);
    assertNotNull(createdSample.getExpiryDate());

    apiSample =
        new ApiSampleWithFullSubSamples("sample from template with expiry date set to empty value");
    apiSample.setExpiryDate(LocalDateDeserialiser.NULL_DATE);
    apiSample.setTemplateId(savedTemplate.getId());
    createdSample = sampleApiMgr.createNewApiSample(apiSample, anyUser);
    assertNotNull(createdSample);
    assertNull(createdSample.getExpiryDate()); // PRT-345
  }

  private ApiSampleTemplate postValidSampleTemplate(ApiSampleTemplatePost sampleTemplatePost)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/sampleTemplates", anyUser, sampleTemplatePost))
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiSampleTemplate putValidSampleTemplate(String templateUpdateJSON, Long templateId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/sampleTemplates/" + templateId, anyUser, templateUpdateJSON))
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiSampleTemplateSearchResult listAllSampleTemplates() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/sampleTemplates", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplateSearchResult.class);
  }

  private ApiSampleTemplate retrieveSampleTemplate(Long id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/sampleTemplates/" + id, anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }

  private ApiSampleTemplate retrieveSampleTemplateVersion(Long id, Long version) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/sampleTemplates/" + id + "/versions/" + version,
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleTemplate.class);
  }
}
