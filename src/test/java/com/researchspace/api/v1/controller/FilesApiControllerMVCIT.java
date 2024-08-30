package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static com.researchspace.model.core.RecordType.API_INBOX;
import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiFileSearchResult;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebAppConfiguration
public class FilesApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getFiles() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    EcatImage galleryImage = addImageToGallery(anyUser);
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/files", anyUser))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiFileSearchResult apiFiles = getFromJsonResponseBody(result, ApiFileSearchResult.class);
    assertNotNull(apiFiles);
    assertEquals(1, apiFiles.getFiles().size());

    ApiFile apiFile = apiFiles.getFiles().get(0);
    System.err.println(
        "apifile "
            + new Date(apiFile.getCreatedMillis())
            + ", file: "
            + galleryImage.getCreationDate());
    apiModelTestUtils.assertApiFileMatchEcatMediaFile(apiFile, galleryImage);
  }

  @Test
  public void getFileById() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    EcatImage galleryImage = addImageToGallery(anyUser);
    MvcResult result = getFile(anyUser, apiKey, galleryImage.getId());

    assertNull(result.getResolvedException());

    ApiFile apiFile = getFromJsonResponseBody(result, ApiFile.class);
    assertNotNull(apiFile);
    apiModelTestUtils.assertApiFileMatchEcatMediaFile(apiFile, galleryImage);
  }

  private MvcResult getFile(User anyUser, String apiKey, Long galleryId) throws Exception {
    return mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/files/{id}", anyUser, galleryId))
        .andReturn();
  }

  @Test
  public void getFileBytes() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    EcatImage galleryImage = addImageToGallery(anyUser);
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/files/{id}/file", anyUser, galleryImage.getId()))
            .andReturn();
    assertNull(result.getResolvedException());

    byte[] responseBytes = result.getResponse().getContentAsByteArray();
    assertNotNull(responseBytes);

    byte[] resourceStream = RSpaceTestUtils.getResourceAsByteArray("Picture1.png");
    assertBytesEqual(resourceStream, responseBytes);
  }

  @Test
  public void updateFile() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MockMultipartFile originalFile = picture1();
    final long originalFileSize = originalFile.getSize();
    ApiFile originalApiFile = doFileUpload(apiKey, originalFile);
    assertEquals(1, originalApiFile.getVersion().intValue());
    String originalFileDownloadlink = getFileDownloadLink(originalApiFile);

    MockMultipartFile newVersion = picture2();
    final long newVersionSize = newVersion.getSize();
    assertTrue(originalFileSize != newVersionSize); // sanity check

    MvcResult newVersionResult =
        mockMvc
            .perform(
                multipart(
                        createUrl(API_VERSION.ONE, "/files/{id}/file"),
                        originalApiFile.getId() + "")
                    .file(newVersion)
                    .header("apiKey", apiKey))
            .andReturn();

    ApiFile apiFileNew = getFromJsonResponseBody(newVersionResult, ApiFile.class);
    assertEquals(2, apiFileNew.getVersion().intValue());
    assertEquals("Picture2.png", apiFileNew.getName());
    assertEquals(newVersionSize, apiFileNew.getSize().longValue());
    String newFileDownloadlink = getFileDownloadLink(apiFileNew);

    assertTrue(newFileDownloadlink.equals(originalFileDownloadlink));
    MvcResult result2 =
        mockMvc
            .perform(MockMvcRequestBuilders.get(newFileDownloadlink).header("apiKey", apiKey))
            .andReturn();

    byte[] responseBytes = result2.getResponse().getContentAsByteArray();
    assertNotNull(responseBytes);

    byte[] resourceStream = RSpaceTestUtils.getResourceAsByteArray("Picture2.png");
    assertBytesEqual(resourceStream, responseBytes);
  }

  void assertBytesEqual(byte[] expected, byte[] actual) {
    assertTrue(Arrays.equals(expected, actual));
  }

  private String getFileDownloadLink(ApiFile apiFileNew) {
    return apiFileNew.getLinks().stream()
        .filter(l -> l.getRel().equals(ApiLinkItem.ENCLOSURE_REL))
        .findAny()
        .get()
        .getLink();
  }

  @Test
  public void uploadFile() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    MockMultipartFile mf = picture1();
    // this will create an API inbox folder
    ApiFile apiFile = doFileUpload(apiKey, mf);
    assertNotNull(apiFile);
    // check file has been created
    Record newMediaFile = recordMgr.get(apiFile.getId());
    assertTrue(newMediaFile.getParent().hasType(API_INBOX));
    assertEquals(IMAGES_MEDIA_FLDER_NAME, newMediaFile.getParent().getParent().getName());

    // this will reuse an API inbox folder and add caption
    final String EXPECTED_CAPTION = "metadata";
    MvcResult result2 =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/files"))
                    .file(mf)
                    .param("caption", EXPECTED_CAPTION)
                    .header("apiKey", apiKey))
            .andReturn();
    ApiFile apiFile2 = getFromJsonResponseBody(result2, ApiFile.class);
    assertEquals(HttpStatus.CREATED.value(), result2.getResponse().getStatus());
    assertEquals(EXPECTED_CAPTION, apiFile2.getCaption());
    // check is persisted properly on new get:
    MvcResult result3 = getFile(anyUser, apiKey, apiFile2.getId());
    ApiFile apiFile3 = getFromJsonResponseBody(result3, ApiFile.class);
    assertEquals(EXPECTED_CAPTION, apiFile3.getCaption());
  }

  private MockMultipartFile picture1() throws IOException, FileNotFoundException {
    return new MockMultipartFile(
        "file", "Picture1.png", "image/png", getTestResourceFileStream("Picture1.png"));
  }

  private MockMultipartFile picture2() throws IOException, FileNotFoundException {
    return new MockMultipartFile(
        "file", "Picture2.png", "image/png", getTestResourceFileStream("Picture2.png"));
  }

  private ApiFile doFileUpload(String apiKey, MockMultipartFile mf) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/files")).file(mf).header("apiKey", apiKey))
            .andReturn();
    assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus());

    ApiFile apiFile = getFromJsonResponseBody(result, ApiFile.class);
    return apiFile;
  }

  @Test
  public void notFoundExceptionThrownIfResourceNotExistsOrNotAuthorised() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    User otherUser = createInitAndLoginAnyUser();
    logoutAndLoginAs(otherUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(otherUser, "apiTest");
    EcatDocumentFile galleryFile = addDocumentToGallery(otherUser);
    logout();
    String apiKey = createNewApiKeyForUser(anyUser);
    // not-existent
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/files/12345", anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // not permitted
    this.mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/files/" + galleryFile.getId(), anyUser))
        .andExpect(status().isNotFound())
        .andReturn();
    // exists, but wrong type: RSPAC1141
    this.mockMvc
        .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/files/" + doc.getId(), anyUser))
        .andExpect(status().isNotFound())
        .andReturn();

    // authentication failure returned in preference to 404 if apikey wrong:
    this.mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, "WRONG KEY", "/files/" + doc.getId(), anyUser))
        .andExpect(status().isUnauthorized())
        .andReturn();
  }
}
