package com.researchspace.webapp.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.*;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.MediaManager;
import com.researchspace.service.ThumbnailManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
public class ThumbnailControllerMVCIT extends MVCTestBase {

  @Autowired private MediaManager mediaManager;

  @Autowired private ThumbnailManager thumbnailManager;

  private MockMvc mockMvc;
  private User testUser;

  @Autowired private WebApplicationContext wac;
  Field field;
  Long documentId;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
    testUser = createInitAndLoginAnyUser();
    field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    documentId = field.getId();
  }

  @Test
  public void testGetThumbnail() throws Exception {
    MockMultipartFile mf =
        new MockMultipartFile("xfile", "image.png", "png", getTestResourceFileStream("tester.png"));
    uploadAndTestThumbnail(mf);
  }

  @Test
  public void testGetThumbnailOfTiff() throws Exception {
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "Picture1.tiff", "png", getTestResourceFileStream("Picture1.tiff"));
    uploadAndTestThumbnail(mf);
  }

  private void uploadAndTestThumbnail(MockMultipartFile mf) throws Exception {
    MvcResult result = doUpload(mf, testUser);

    Long id = extractSourceId(result);

    final int thumbnailSize = 50;
    byte[] b = getThumbnail(id, thumbnailSize, documentId, testUser, null, "image/png");

    BufferedImage thumbnailImage = ImageIO.read(new ByteArrayInputStream(b));

    assertEquals(Math.max(thumbnailImage.getWidth(), thumbnailImage.getHeight()), thumbnailSize);
  }

  private Long extractSourceId(MvcResult result) throws IOException {
    Map<String, Object> json = convertJsonStringToMap(result.getResponse().getContentAsString());
    assertNotNull(json.get("data"));
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Long id = Long.valueOf((Integer) data.get("id"));
    assertNotNull(id);
    return id;
  }

  private byte[] getThumbnail(
      Long sourceId,
      final int thumbnailSize,
      final long sourceParentId,
      User user,
      Class<? extends Exception> expectedException,
      String expectedMimeType)
      throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                get("/thumbnail/data")
                    .principal(new MockPrincipal(user.getUsername()))
                    .param("sourceId", sourceId + "")
                    .param("sourceParentId", sourceParentId + "")
                    .param("sourceType", "IMAGE")
                    .param("width", thumbnailSize + "")
                    .param("height", thumbnailSize + ""))
            .andReturn();
    if (expectedException == null) {
      assertEquals(expectedMimeType, result.getResponse().getContentType());
    } else {
      assertEquals(expectedException, result.getResolvedException().getClass());
    }

    return result.getResponse().getContentAsByteArray();
  }

  MvcResult doUpload(MockMultipartFile mf, User user) throws Exception {
    return mockMvc
        .perform(
            multipart("/gallery/ajax/uploadFile")
                .file(mf)
                .principal(new MockPrincipal(user.getUsername())))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  public void testThumbnailPermissions() throws Exception {
    MockMultipartFile mf =
        new MockMultipartFile("xfile", "image.png", "png", getTestResourceFileStream("tester.png"));
    MvcResult result = doUpload(mf, testUser);
    final Long id = extractSourceId(result);

    final User user2 = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user2);
    logoutAndLoginAs(user2);
    // user 2 can't access thumnail...
    final int thumbnailSize = 50;

    getThumbnail(id, thumbnailSize, documentId, user2, AuthorizationException.class, "");

    StructuredDocument user2Doc = createBasicDocumentInRootFolderWithText(user2, "any");
    Long user2FieldId = user2Doc.getFields().get(0).getId();
    // ... but he can if he sets his own field id...
    getThumbnail(id, thumbnailSize, user2FieldId, user2, AuthorizationException.class, "");
  }

  @Test
  public void testRevisionChemThumbnail() throws Exception {

    String newChemElementMolString = RSpaceTestUtils.getExampleChemString();
    String imageBase64 = RSpaceTestUtils.getChemImage();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(newChemElementMolString)
            .imageBase64(imageBase64)
            .fieldId(field.getId())
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();
    RSChemElement rsChemElement = rsChemElementManager.saveChemElement(chemicalData, testUser);

    final int thumbnailSize = 50;
    byte[] data =
        mockMvc
            .perform(
                get("/thumbnail/data")
                    .principal(new MockPrincipal(piUser.getUsername()))
                    .param("sourceId", rsChemElement.getId() + "")
                    .param("sourceType", "CHEM")
                    .param("width", thumbnailSize + "")
                    .param("height", thumbnailSize + "")
                    .param("revision", "1"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertNotNull(data);
  }

  protected Map<String, Object> convertJsonStringToMap(String json) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(json, new TypeReference<HashMap<String, Object>>() {});
  }

  @Test
  public void testLegacyThumbnailRetrieval() throws Exception {
    // First we upload a picture 3 times
    MockMultipartFile mf1 =
        new MockMultipartFile("xfile", "image.png", "png", getTestResourceFileStream("tester.png"));
    MvcResult uploadResult1 = doUpload(mf1, testUser);
    Long JPEG_ID = extractSourceId(uploadResult1);

    MockMultipartFile mf2 =
        new MockMultipartFile("xfile", "image.png", "png", getTestResourceFileStream("tester.png"));
    MvcResult uploadResult2 = doUpload(mf2, testUser);
    Long TIFF_ID = extractSourceId(uploadResult2);

    MockMultipartFile mf3 =
        new MockMultipartFile("xfile", "image.png", "png", getTestResourceFileStream("tester.png"));
    MvcResult uploadResult3 = doUpload(mf3, testUser);
    Long STRANGE_ID = extractSourceId(uploadResult3);

    // Then we replace the file's thumbnail with one that's not in PNG format
    Thumbnail jpegThumbnail = new Thumbnail();
    Thumbnail tiffThumbnail = new Thumbnail();
    Thumbnail strangeThumbnail = new Thumbnail();

    jpegThumbnail.setSourceId(JPEG_ID);
    tiffThumbnail.setSourceId(TIFF_ID);
    strangeThumbnail.setSourceId(STRANGE_ID);

    jpegThumbnail.setSourceType(Thumbnail.SourceType.IMAGE);
    tiffThumbnail.setSourceType(Thumbnail.SourceType.IMAGE);
    strangeThumbnail.setSourceType(Thumbnail.SourceType.IMAGE);

    jpegThumbnail.setSourceParentId(documentId);
    tiffThumbnail.setSourceParentId(documentId);
    strangeThumbnail.setSourceParentId(documentId);

    jpegThumbnail.setHeight(50);
    tiffThumbnail.setHeight(50);
    strangeThumbnail.setHeight(50);

    jpegThumbnail.setWidth(50);
    tiffThumbnail.setWidth(50);
    strangeThumbnail.setWidth(50);

    byte[] jpegBytes = IOUtils.toByteArray(getTestResourceFileStream("spray.jpg"));
    byte[] tiffBytes = IOUtils.toByteArray(getTestResourceFileStream("Picture1.tiff"));
    byte[] strangeBytes = IOUtils.toByteArray(getTestResourceFileStream("small.mp4"));

    jpegThumbnail.setImageBlob(new ImageBlob(jpegBytes));
    tiffThumbnail.setImageBlob(new ImageBlob(tiffBytes));
    strangeThumbnail.setImageBlob(new ImageBlob(strangeBytes));

    thumbnailManager.save(jpegThumbnail, testUser);
    thumbnailManager.save(tiffThumbnail, testUser);
    thumbnailManager.save(strangeThumbnail, testUser);

    // Attempt to get saved thumbnails, verify mime-types
    byte[] receivedJpeg = getThumbnail(JPEG_ID, 50, documentId, testUser, null, "image/jpeg");
    byte[] receivedTiff = getThumbnail(TIFF_ID, 50, documentId, testUser, null, "image/tiff");
    byte[] receivedStrange =
        getThumbnail(STRANGE_ID, 50, documentId, testUser, null, "application/octet-stream");

    assertArrayEquals(jpegBytes, receivedJpeg);
    assertArrayEquals(tiffBytes, receivedTiff);
    assertArrayEquals(strangeBytes, receivedStrange);
  }
}
