package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class InventoryAttachmentsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void addRetrieveDeleteInventoryGalleryAttachment() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer apiContainer = createBasicContainerForUser(anyUser);
    assertEquals(0, apiContainer.getAttachments().size());

    EcatImage galleryImage = addImageToGallery(anyUser);
    assertEquals("Picture1.png", galleryImage.getName());

    // upload file as attachment to container
    String settingsJson =
        "{ \"parentGlobalId\": \""
            + apiContainer.getGlobalId()
            + "\", \"mediaFileGlobalId\": \""
            + galleryImage.getOid().toString()
            + "\" }";
    ApiInventoryFile uploadedApiFile = doAttachGalleryFilePost(apiKey, settingsJson, anyUser);
    assertNotNull(uploadedApiFile);

    // retrieve attached file by id
    long attachmentId = uploadedApiFile.getId();
    MvcResult result = getFile(anyUser, apiKey, attachmentId);
    assertNull(result.getResolvedException());
    ApiInventoryFile apiFile = getFromJsonResponseBody(result, ApiInventoryFile.class);
    assertNotNull(apiFile);
    assertEquals("Picture1.png", apiFile.getName());
    assertEquals(galleryImage.getOid().toString(), apiFile.getMediaFileGlobalId());

    // retrieve file content
    result = getFileContent(anyUser, apiKey, attachmentId);
    assertNull(result.getResolvedException());
    byte[] responseBytes = result.getResponse().getContentAsByteArray();
    assertNotNull(responseBytes);
    assertEquals(72169, responseBytes.length);

    // check latest container lists the file
    MvcResult retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, apiContainer.getId(), false))
            .andReturn();
    apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertEquals(1, apiContainer.getAttachments().size());

    // delete the file
    result = doFileDelete(anyUser, apiKey, attachmentId);
    assertNull(result.getResolvedException());

    // check latest container doesn't list the file
    retrieveResult =
        this.mockMvc
            .perform(getContainerById(anyUser, apiKey, apiContainer.getId(), false))
            .andReturn();
    apiContainer = getFromJsonResponseBody(retrieveResult, ApiContainer.class);
    assertEquals(0, apiContainer.getAttachments().size());
  }

  private ApiInventoryFile doAttachGalleryFilePost(
      String apiKey, String attachSettingsJson, User user) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/attachments", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(attachSettingsJson))
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus());

    return getFromJsonResponseBody(result, ApiInventoryFile.class);
  }

  private MvcResult getFile(User anyUser, String apiKey, long attachmentId) throws Exception {
    return mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/files/{id}", anyUser, attachmentId))
        .andReturn();
  }

  private MvcResult getFileContent(User anyUser, String apiKey, long attachmentId)
      throws Exception {
    return mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/files/{id}/file", anyUser, attachmentId))
        .andReturn();
  }

  private MvcResult doFileDelete(User anyUser, String apiKey, long attachmentId) throws Exception {
    return mockMvc
        .perform(createBuilderForDelete(apiKey, "/files/{id}", anyUser, attachmentId))
        .andReturn();
  }
}
