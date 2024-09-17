package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
@TestPropertySource(properties = "chemistry.web.url=http://howler.researchspace.com:8099")
public class InventoryFilesApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void uploadRetrieveDeleteInventoryFileAttachment() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer apiContainer = createBasicContainerForUser(anyUser);
    assertEquals(0, apiContainer.getAttachments().size());

    // upload file as attachment to container
    MockMultipartFile originalFile = picture1();
    String settingsJson =
        "{ \"fileName\": \"exampleFileName.txt\", \"parentGlobalId\": \""
            + apiContainer.getGlobalId()
            + "\" }";
    ApiInventoryFile uploadedApiFile = doFileUpload(apiKey, originalFile, settingsJson);
    assertNotNull(uploadedApiFile);

    // retrieve file by id
    long attachmentId = uploadedApiFile.getId();
    MvcResult result = getFile(anyUser, apiKey, attachmentId);
    assertNull(result.getResolvedException());
    ApiInventoryFile apiFile = getFromJsonResponseBody(result, ApiInventoryFile.class);
    assertNotNull(apiFile);
    assertEquals("exampleFileName.txt", apiFile.getName());

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

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void uploadRetrieveImageInventoryFileAttachment() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    ApiContainer apiContainer = createBasicContainerForUser(anyUser);
    assertEquals(0, apiContainer.getAttachments().size());

    // upload file as attachment to container
    MockMultipartFile originalFile = getChemicalMockFile();
    String settingsJson =
        "{ \"fileName\": \"chemical.mol\", \"parentGlobalId\": \""
            + apiContainer.getGlobalId()
            + "\" }";
    ApiInventoryFile uploadedApiFile = doFileUpload(apiKey, originalFile, settingsJson);
    assertNotNull(uploadedApiFile);

    // retrieve file by id
    long attachmentId = uploadedApiFile.getId();
    MvcResult result = getFile(anyUser, apiKey, attachmentId);
    assertNull(result.getResolvedException());
    ApiInventoryFile apiFile = getFromJsonResponseBody(result, ApiInventoryFile.class);
    assertNotNull(apiFile);
    assertEquals("chemical.mol", apiFile.getName());

    // Standard sunny day request
    String imageRequest = "{ \"height\": 500, \"width\": 500, \"scale\": 50.0 }";
    result = getFileImage(anyUser, apiKey, attachmentId, imageRequest);
    assertNull(result.getResolvedException());
    assertEquals("image/png", result.getResponse().getContentType());
    byte[] responseBytes = result.getResponse().getContentAsByteArray();
    assertNotNull(responseBytes);

    // No params should still return a default image of 200x200
    String imageRequest2 = "{}";
    result = getFileImage(anyUser, apiKey, attachmentId, imageRequest2);
    assertNull(result.getResolvedException());
    assertEquals("image/png", result.getResponse().getContentType());
    byte[] responseImage = result.getResponse().getContentAsByteArray();
    InputStream in = new ByteArrayInputStream(responseImage);
    BufferedImage buffered = ImageIO.read(in);
    assertEquals(200, buffered.getHeight());
    assertEquals(200, buffered.getWidth());
  }

  @Test
  public void retrieveImageViaFilenameSuccess() throws Exception {
    // create basic sample
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    String apiKey = createApiKeyForuser(user);

    // update sample with image
    InputStream imageFile = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());
    sample.setNewBase64Image(imageBytes);
    sampleApiMgr.updateApiSample(sample, user);

    // retrieve image via filename
    String fileName = CryptoUtils.hashWithSha256inHex(imageBytes) + ".png";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/files/image/{fileName}", user, fileName))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String responseImage = result.getResponse().getContentAsString();
    assertNotNull(responseImage);
  }

  @Test
  public void retrieveImageViaFilenameReturnsUnauthorizedWhenNotPermitted() throws Exception {
    // create basic sample
    User user1 = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user1);

    // update sample with image
    InputStream imageFile = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    String imageBytes = Arrays.toString(imageFile.readAllBytes());
    sample.setNewBase64Image(imageBytes);
    sampleApiMgr.updateApiSample(sample, user1);

    // try to retrieve image as user2, who isn't authorised
    User user2 = createInitAndLoginAnyUser();
    String user2ApiKey = createApiKeyForuser(user2);
    String fileName = CryptoUtils.hashWithSha256inHex(imageBytes) + ".png";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, user2ApiKey, "/files/image/{fileName}", user1, fileName))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertEquals(
        String.format("User doesn't have permissions to read image file %s.", fileName),
        result.getResolvedException().getMessage());
  }

  @Test
  public void retrieveNonExistentImageReturnsNotFound() throws Exception {
    User user = createInitAndLoginAnyUser();
    String apikey = createApiKeyForuser(user);
    String nonExistentFile = "abc123.some.file";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apikey, "/files/image/{fileName}", user, nonExistentFile))
            .andExpect(status().isNotFound())
            .andReturn();

    assertEquals("Image abc123.some.file not found.", result.getResolvedException().getMessage());
  }

  private MockMultipartFile picture1() throws IOException {
    return new MockMultipartFile(
        "file", "Picture1.png", "image/png", getTestResourceFileStream("Picture1.png"));
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

  private MvcResult getFileImage(User anyUser, String apiKey, long attachmentId, String imageParams)
      throws Exception {
    return mockMvc
        .perform(
            createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/files/{id}/file/image", anyUser, attachmentId)
                .param("imageParams", imageParams))
        .andReturn();
  }

  private ApiInventoryFile doFileUpload(
      String apiKey, MockMultipartFile mf, String fileSettingsJson) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/files"))
                    .file(mf)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("fileSettings", fileSettingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus());

    return getFromJsonResponseBody(result, ApiInventoryFile.class);
  }

  private MvcResult doFileDelete(User anyUser, String apiKey, long attachmentId) throws Exception {
    return mockMvc
        .perform(createBuilderForDelete(apiKey, "/files/{id}", anyUser, attachmentId))
        .andReturn();
  }
}
