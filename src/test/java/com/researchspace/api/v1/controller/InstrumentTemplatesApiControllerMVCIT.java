package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntityInfo;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class InstrumentTemplatesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  User anyUser;
  String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  private ApiInstrumentTemplatePost createValidPostWithOneField() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("mvc-template");
    templatePost.setApiTagInfo("tag1,tag2");
    templatePost.getFields().add(createBasicApiSampleField("F1", ApiFieldType.TEXT, "default"));
    return templatePost;
  }

  @Test
  public void createListGetDeleteRestoreRoundTrip() throws Exception {
    int initialCount = listAllInstrumentTemplates().getTotalHits().intValue();

    // POST
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());
    assertNotNull(created.getId());
    assertTrue(created.isTemplate());
    assertEquals("mvc-template", created.getName());
    assertEquals(GlobalIdPrefix.NT.name(), created.getGlobalId().substring(0, 2));
    assertEquals(1L, (long) created.getVersion());
    assertEquals(1, created.getFields().size());
    assertTrue(created.getLinkOfType(ApiLinkItem.SELF_REL).isPresent());

    // GET (list)
    assertEquals(initialCount + 1, listAllInstrumentTemplates().getTotalHits().intValue());

    // GET (by id)
    ApiInstrumentTemplate fetched = retrieveInstrumentTemplate(created.getId());
    assertEquals(created.getId(), fetched.getId());
    assertEquals(1, fetched.getFields().size());

    // DELETE
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/instrumentTemplates/{id}", anyUser, created.getId()))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    assertEquals(initialCount, listAllInstrumentTemplates().getTotalHits().intValue());

    // RESTORE
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instrumentTemplates/" + created.getId() + "/restore", anyUser, null))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
    assertEquals(initialCount + 1, listAllInstrumentTemplates().getTotalHits().intValue());
  }

  @Test
  public void putBumpsVersionAndAddsField() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());
    long initialVersion = created.getVersion();

    // Note: `newFieldRequest` is JSON write-only on the model, so Jackson won't serialize it out
    // when sending Java -> JSON. The request body therefore has to be constructed as raw JSON so
    // the server sees `newFieldRequest=true` on deserialise.
    String updateJson =
        "{ \"name\": \"renamed-template\","
            + " \"fields\": ["
            + " { \"name\": \"F2\", \"type\": \"NUMBER\", \"newFieldRequest\": true }"
            + " ] }";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instrumentTemplates/" + created.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();

    ApiInstrumentTemplate updated = getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
    assertEquals("renamed-template", updated.getName());
    assertEquals(initialVersion + 1, (long) updated.getVersion());
    assertEquals(2, updated.getFields().size());
  }

  @Test
  public void getInstrumentTemplateByVersionReturnsHistoricalSnapshot() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    // bump to version 2
    ApiInstrumentTemplate update = new ApiInstrumentTemplate();
    update.setName("renamed");
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instrumentTemplates/" + created.getId(), anyUser, update))
        .andExpect(status().isOk())
        .andReturn();

    // current version
    ApiInstrumentTemplate current = retrieveInstrumentTemplate(created.getId());
    assertEquals(2L, (long) current.getVersion());
    assertEquals("renamed", current.getName());

    // historical version 1
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instrumentTemplates/" + created.getId() + "/versions/1",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrumentTemplate v1 = getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
    assertNotNull(v1);
    assertEquals(1L, (long) v1.getVersion());
    assertTrue(v1.isHistoricalVersion());
    assertEquals("mvc-template", v1.getName());
  }

  @Test
  public void postRejectsBlankNameAndDuplicateFields() throws Exception {
    // blank name
    ApiInstrumentTemplatePost blank = new ApiInstrumentTemplatePost();
    blank.setName(" ");
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instrumentTemplates", anyUser, blank))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertTrue(error.getErrors().stream().anyMatch(e -> e.contains("name")));

    // duplicate field names
    ApiInstrumentTemplatePost dupFields = createValidPostWithOneField();
    dupFields.getFields().add(createBasicApiSampleField("F1", ApiFieldType.TEXT, "second"));
    result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, dupFields))
            .andExpect(status().is4xxClientError())
            .andReturn();
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertTrue(
        error.getErrors().stream().anyMatch(e -> e.contains("fields[1].name")),
        "expected duplicate-name error, got: " + error.getErrors());
  }

  @Test
  public void changeOwnerTransfersTemplate() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    User newOwner = createInitAndLoginAnyUser();
    String changeOwnerJson = "{ \"owner\": { \"username\": \"" + newOwner.getUsername() + "\" } }";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey,
                    "/instrumentTemplates/" + created.getId() + "/actions/changeOwner",
                    anyUser,
                    changeOwnerJson))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrumentTemplate transferred =
        getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());
  }

  @Test
  public void setAndGetIcon() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());
    MockMultipartFile iconFile =
        new MockMultipartFile(
            "file", "Picture1.png", "image/png", getTestResourceFileStream("Picture1.png"));

    MvcResult result =
        mockMvc
            .perform(
                multipart(
                        createUrl(
                            API_VERSION.ONE, "/instrumentTemplates/" + created.getId() + "/icon"))
                    .file(iconFile)
                    .header("apiKey", apiKey))
            .andReturn();
    ApiInstrumentEntityInfo withIcon =
        getFromJsonResponseBody(result, ApiInstrumentEntityInfo.class);
    assertNotNull(withIcon.getIconId());
    assertTrue(withIcon.getIconId() > 0);

    MvcResult iconResp =
        mockMvc
            .perform(
                createBuilderForGet2(
                    API_VERSION.ONE,
                    apiKey,
                    "/instrumentTemplates/{id}/icon/{iconId}",
                    () -> anyUser::getUsername,
                    withIcon.getId(),
                    withIcon.getIconId()))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(iconResp.getResponse().getContentAsByteArray().length > 0);
  }

  @Test
  public void iconUploadByReaderButNotEditorIsRefused() throws Exception {
    // A sysadmin can READ any record via the admin override but is not the owner and has no
    // sharing-based edit right, so it is a reader that is not an editor. Changing a template's
    // icon is a mutation and must require EDIT, not just READ (RSDEV-1219 Part J). Before the fix
    // the icon endpoint asserted only READ, so any non-editor reader could overwrite the icon.
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    String sysadminApiKey = createNewApiKeyForUser(getSysAdminUser());
    MockMultipartFile iconFile =
        new MockMultipartFile(
            "file", "Picture1.png", "image/png", getTestResourceFileStream("Picture1.png"));

    mockMvc
        .perform(
            multipart(
                    createUrl(API_VERSION.ONE, "/instrumentTemplates/" + created.getId() + "/icon"))
                .file(iconFile)
                .header("apiKey", sysadminApiKey))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void imageAndThumbnailReturn404WhenNotSet() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    mockMvc
        .perform(
            createBuilderForGet2(
                API_VERSION.ONE,
                apiKey,
                "/instrumentTemplates/{id}/image/{ts}",
                () -> anyUser::getUsername,
                created.getId(),
                0L))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            createBuilderForGet2(
                API_VERSION.ONE,
                apiKey,
                "/instrumentTemplates/{id}/thumbnail/{ts}",
                () -> anyUser::getUsername,
                created.getId(),
                0L))
        .andExpect(status().isNotFound());
  }

  @Test
  public void updateInstrumentsToLatestTemplateVersion_bulkResyncs() throws Exception {
    // Create a template and an instrument from it
    ApiInstrumentTemplate template = postValidInstrumentTemplate(createValidPostWithOneField());
    String createInstrumentJson =
        "{ \"name\": \"i-from-tmpl\", \"templateId\": " + template.getId() + " }";
    MvcResult instrumentResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instruments", anyUser, createInstrumentJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrument instrument = getFromJsonResponseBody(instrumentResult, ApiInstrument.class);
    assertEquals(template.getId(), instrument.getTemplateId());
    assertEquals(template.getVersion(), instrument.getTemplateVersion());

    // Bump the template (add a new field). Raw JSON so `newFieldRequest` is serialised.
    String bumpJson =
        "{ \"fields\": ["
            + " { \"name\": \"addedLater\", \"type\": \"TEXT\", \"newFieldRequest\": true }"
            + " ] }";
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/instrumentTemplates/" + template.getId(), anyUser, bumpJson))
        .andExpect(status().isOk())
        .andReturn();

    // Bulk re-sync
    mockMvc
        .perform(
            createBuilderForPost(
                API_VERSION.ONE,
                apiKey,
                "/instrumentTemplates/"
                    + template.getId()
                    + "/actions/updateInstrumentsToLatestTemplateVersion",
                anyUser))
        .andExpect(status().isOk())
        .andReturn();

    // verify the instrument now points to the new template version
    MvcResult retrieved =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/instruments/" + instrument.getId(), anyUser))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrument resynced = getFromJsonResponseBody(retrieved, ApiInstrument.class);
    assertTrue(resynced.getTemplateVersion() > instrument.getTemplateVersion());
    assertTrue(
        resynced.getFields().stream().anyMatch(f -> "addedLater".equals(f.getName())),
        "expected the new template field to be propagated to the instrument");
  }

  @Test
  public void deletingInstrumentTemplateReturnsTemplateInBody() throws Exception {
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForDelete(
                    apiKey, "/instrumentTemplates/{id}", anyUser, created.getId()))
            .andExpect(status().isOk())
            .andReturn();
    ApiInstrumentTemplate deleted = getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
    assertNotNull(deleted);
    assertTrue(deleted.isDeleted());
    assertFalse(deleted.getDeletedDate() == null);
  }

  @Test
  public void getNonExistingInstrumentTemplateReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/instrumentTemplates/9999999", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  public void instrumentsApiManagerCreatesViaTemplateId() throws Exception {
    // sanity end-to-end: create a template + then create an instrument referencing it
    ApiInstrumentTemplate template = postValidInstrumentTemplate(createValidPostWithOneField());
    String createInstrumentJson =
        "{ \"name\": \"linked\", \"templateId\": " + template.getId() + " }";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instruments", anyUser, createInstrumentJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrument created = getFromJsonResponseBody(result, ApiInstrument.class);
    assertEquals(template.getId(), created.getTemplateId());
    assertEquals(template.getVersion(), created.getTemplateVersion());
    assertEquals(1, created.getFields().size());
    assertEquals("F1", created.getFields().get(0).getName());
    assertNull(result.getResolvedException());
  }

  // ---- helpers ----

  @Test
  public void createInstrumentTemplateWithImage() throws Exception {
    // POST a template carrying a base64 image — server should accept and link it.
    String createJson = "{ \"name\": \"image-template\", \"newBase64Image\": \"" + BASE_64 + "\" }";

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, createJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertNull(createResult.getResolvedException());

    ApiInstrumentTemplate created =
        getFromJsonResponseBody(createResult, ApiInstrumentTemplate.class);
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("image-template", created.getName());
    assertTrue(
        created.getLinkOfType(ApiLinkItem.IMAGE_REL).isPresent(),
        "expected IMAGE_REL link on a template created with newBase64Image");
    assertTrue(
        created.getLinkOfType(ApiLinkItem.THUMBNAIL_REL).isPresent(),
        "expected THUMBNAIL_REL link on a template created with newBase64Image");
  }

  @Test
  public void updateInstrumentTemplateReplacesImage() throws Exception {
    // Start with an imageless template, then PUT a body that sets a new base64 image.
    ApiInstrumentTemplate created = postValidInstrumentTemplate(createValidPostWithOneField());

    String updateJson = "{ \"newBase64Image\": \"" + BASE_64 + "\" }";
    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/instrumentTemplates/" + created.getId(), anyUser, updateJson))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(updateResult.getResolvedException());

    ApiInstrumentTemplate updated =
        getFromJsonResponseBody(updateResult, ApiInstrumentTemplate.class);
    assertTrue(
        updated.getLinkOfType(ApiLinkItem.IMAGE_REL).isPresent(),
        "expected IMAGE_REL link on a template after updating with newBase64Image");
    assertTrue(
        updated.getLinkOfType(ApiLinkItem.THUMBNAIL_REL).isPresent(),
        "expected THUMBNAIL_REL link on a template after updating with newBase64Image");
  }

  @Test
  public void getInstrumentTemplateImageAndThumbnail() throws Exception {
    // Create a template with an image, then fetch its image and thumbnail as bytes.
    String createJson =
        "{ \"name\": \"image-template-get\", \"newBase64Image\": \"" + BASE_64 + "\" }";
    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, createJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiInstrumentTemplate created =
        getFromJsonResponseBody(createResult, ApiInstrumentTemplate.class);

    // GET image
    MvcResult imageResult =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instrumentTemplates/" + created.getId() + "/image/0",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    byte[] imageBytes = imageResult.getResponse().getContentAsByteArray();
    assertTrue(imageBytes.length > 0, "image response body should be non-empty");
    String imageContentType = imageResult.getResponse().getContentType();
    assertNotNull(imageContentType);
    assertTrue(
        imageContentType.toLowerCase().startsWith("image/"),
        "image content-type should start with image/, got: " + imageContentType);

    // GET thumbnail
    MvcResult thumbResult =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE,
                    apiKey,
                    "/instrumentTemplates/" + created.getId() + "/thumbnail/0",
                    anyUser))
            .andExpect(status().isOk())
            .andReturn();
    byte[] thumbBytes = thumbResult.getResponse().getContentAsByteArray();
    assertTrue(thumbBytes.length > 0, "thumbnail response body should be non-empty");
    // thumbnail should be a smaller (or equal) byte payload than the full image for this asset
    assertTrue(
        thumbBytes.length <= imageBytes.length,
        "thumbnail should not be larger than the original image, image="
            + imageBytes.length
            + " thumb="
            + thumbBytes.length);
    String thumbContentType = thumbResult.getResponse().getContentType();
    assertNotNull(thumbContentType);
    assertTrue(
        thumbContentType.toLowerCase().startsWith("image/"),
        "thumbnail content-type should start with image/, got: " + thumbContentType);
  }

  private ApiInstrumentTemplate postValidInstrumentTemplate(ApiInstrumentTemplatePost post)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/instrumentTemplates", anyUser, post))
            .andExpect(status().isCreated())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
  }

  private ApiInstrumentTemplateSearchResult listAllInstrumentTemplates() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/instrumentTemplates", anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInstrumentTemplateSearchResult.class);
  }

  private ApiInstrumentTemplate retrieveInstrumentTemplate(Long id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/instrumentTemplates/" + id, anyUser))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInstrumentTemplate.class);
  }
}
