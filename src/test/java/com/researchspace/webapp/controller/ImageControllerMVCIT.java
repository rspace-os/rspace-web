package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.ImageController.RotationConfig;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestPropertySource(
    properties = {"sample.png=src/test/resources/TestResources/GalleryThumbnail54x76.png"})
public class ImageControllerMVCIT extends MVCTestBase {

  private User testUser, other;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    final int n = 10;
    testUser = createAndSaveUser(getRandomName(n));
    other = createAndSaveUser(getRandomName(n));
    initUsers(testUser, other);
    logoutAndLoginAs(testUser);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    RSpaceTestUtils.logout();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void saveLoadImage() throws Exception {
    // Load the initial dimensions of our image
    Image testImage = null;
    try (InputStream imageInputStream = getTestResourceFileStream("Picture1.png")) {
      testImage = ImageIO.read(imageInputStream);
    }

    int testImageWidth = testImage.getWidth(null);
    MockMultipartFile mf = getTestImage();
    MvcResult result =
        mockMvc
            .perform(
                fileUpload("/gallery/ajax/uploadFile")
                    .file(mf)
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().isOk())
            .andReturn();

    Map<String, Object> json = parseJSONObjectFromResponseStream(result);
    assertNotNull(json.get("data"));
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Integer id = (Integer) data.get("id");
    assertNotNull(id);

    // Now retrieve using parsed id:
    // simulate parent id not being a field id, i.e., it's jsut an image in
    // the gallery
    assertNotNull(
        mockMvc
            .perform(
                get("/image/getImage/{id}/{unused}", "1234-" + id, "1234")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andReturn()
            .getResponse()
            .getContentAsByteArray());

    // Calling without fullImage parameter should return working copy since
    // Picture1.png's width exceeds our maximum of 644 pixels
    byte[] workingCopyBytes =
        mockMvc
            .perform(
                get("/image/getImage/{id}/{unused}", "1234-" + id, "1234")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    Image workingCopy = getImageFromBytes(workingCopyBytes);

    assertEquals(EcatImage.MAX_PAGE_DISPLAY_WIDTH, workingCopy.getWidth(null));

    // Calling with fullImage parameter=true should return working copy as
    // well
    workingCopyBytes =
        mockMvc
            .perform(
                get("/image/getImage/{id}/{unused}?fullImage=false", "1234-" + id, "1234")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    workingCopy = getImageFromBytes(workingCopyBytes);

    assertEquals(EcatImage.MAX_PAGE_DISPLAY_WIDTH, workingCopy.getWidth(null));

    // Calling with fullImage parameter=true should return our full image
    // equal to when we loaded it from the test resource folder directly
    byte[] fullImageBytes =
        mockMvc
            .perform(
                get("/image/getImage/{id}/{unused}?fullImage=true", "1234-" + id, "1234")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    Image fullImage = getImageFromBytes(fullImageBytes);

    assertEquals(testImageWidth, fullImage.getWidth(null));

    // now check that exception thrown if id does not match correct format
    assertTrue(
        mockMvc
                .perform(
                    get("/image/getImage/{id}/{unused}?fullImage=true", "WRONGIDSYNTAX", "1234")
                        .principal(new MockPrincipal(testUser.getUsername())))
                .andReturn()
                .getResolvedException()
            instanceof IllegalArgumentException);
  }

  private BufferedImage getImageFromBytes(byte[] fullImageBytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(fullImageBytes));
  }

  private MockMultipartFile getTestImage() throws IOException, FileNotFoundException {
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    return mf;
  }

  @Test
  public void rotateImage() throws Exception {
    mockPrincipal = new MockPrincipal(testUser.getUsername());
    EcatImage smallImage = addImageToGallery(testUser, "Picture1_small.png");
    rotateAndExpectSuccess(smallImage);

    EcatImage largeImage = addImageToGallery(testUser, "Picture1.png");
    rotateAndExpectSuccess(largeImage);

    MvcResult res;
    Map response;
    // now try with too many ids
    Long[] ids = new Long[200]; // too many
    Arrays.fill(ids, 1L);

    res = doPostRotateImage(Arrays.asList(ids), 2);
    response = parseJSONObjectFromResponseStream(res);
    assertNotNull(response.get("errorMsg"));
    assertNull(response.get("data"));
    // invalid number of rotations
    res = doPostRotateImage(Arrays.asList(smallImage.getId()), 35);
    response = parseJSONObjectFromResponseStream(res);
    assertNotNull(response.get("errorMsg"));
    assertNull(response.get("data"));

    User other = createInitAndLoginAnyUser();
    mockPrincipal = new MockPrincipal(other.getUsername());
    res = doPostRotateImage(Arrays.asList(smallImage.getId()), 1);
    assertNotNull(response.get("errorMsg"));
    assertNull(response.get("data"));
    assertAuthorizationException(res);
  }

  private MvcResult doPostRotateImage(List<Long> values, int timesToRotate) throws Exception {
    RotationConfig cfg = new RotationConfig();
    cfg.setIdsToRotate(values);
    cfg.setTimesToRotate((byte) timesToRotate);
    return mockMvc
        .perform(
            post("/image/ajax/rotateImageGalleries")
                .content(JacksonUtil.toJson(cfg))
                .contentType(MediaType.APPLICATION_JSON)
                .principal(mockPrincipal))
        .andReturn();
  }

  private void rotateAndExpectSuccess(EcatImage image)
      throws Exception, JsonProcessingException, IOException {
    RotationConfig cfg = new RotationConfig();
    cfg.setIdsToRotate(Arrays.asList(image.getId()));
    cfg.setTimesToRotate((byte) 2);
    MvcResult res =
        mockMvc
            .perform(
                post("/image/ajax/rotateImageGalleries")
                    .content(JacksonUtil.toJson(cfg))
                    .contentType(MediaType.APPLICATION_JSON)
                    .principal(mockPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(res.getResolvedException());
    Map response = parseJSONObjectFromResponseStream(res);
    assertTrue(Boolean.parseBoolean(response.get("data").toString()));
    assertNull(response.get("errorsMsg"));
  }

  @Test
  public void getImageToAnnotate() throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    final Field fld = doc.getFields().get(0);
    EcatImage image = addImageToField(fld, testUser);
    byte[] data =
        mockMvc
            .perform(
                getImageToAnnotate(fld, image).principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Image fullImage = getImageFromBytes(data);
    assertNotNull(fullImage);

    // now login as someone else ,can't access:
    logoutAndLoginAs(other);
    MvcResult result =
        mockMvc
            .perform(
                getImageToAnnotate(fld, image).principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assertAuthorizationException(result);
  }

  @Test
  public void getUploadedImageInfo() throws Exception {

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    final Field fld = doc.getFields().get(0);
    EcatImage image = addImageToField(fld, testUser);

    MvcResult response =
        mockMvc
            .perform(
                get("/image/ajax/imageInfo?ids[]=" + image.getId())
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    List<Map> imageInfoList = getFromJsonResponseBody(response, List.class);
    assertNotNull(imageInfoList);
    assertEquals(1, imageInfoList.size());

    Map imageInfo = imageInfoList.get(0);
    assertEquals(image.getId() + "", imageInfo.get("id") + "");
    assertEquals(image.getWidth(), imageInfo.get("width"));
    assertEquals(image.getHeight(), imageInfo.get("height"));
    assertEquals(image.getName(), imageInfo.get("name"));
    assertEquals(image.getDescription(), imageInfo.get("description"));
  }

  private MockHttpServletRequestBuilder getImageToAnnotate(final Field fld, EcatImage image) {
    return get("/image/getImageToAnnotate/{id}/{unused}", fld.getId() + "-" + image.getId(), "any");
  }

  @Test
  public void loadRevisionAfterCancelRSPAC_939() throws Exception {
    // follow steps of this bug report and check revision can be retrieved
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    final Field field = doc.getFields().get(0);
    String base64Image = getBase64Image();
    // create 2nd annotation, autosave and do real save
    String annot = RSpaceTestUtils.getAnyAnnotationOrSketchJson();
    EcatImage img = addImageToGallery(testUser);

    EcatImageAnnotation ann =
        postSaveImageAnnotation(annot, base64Image, img.getId(), field.getId());
    String annotationLink = richTextUpdater.generateAnnotatedImageElement(ann, field.getId() + "");
    doAutosaveAndSaveMVC(field, annotationLink, testUser);

    // create 2nd annotation, autosave and cancel
    EcatImage img2 = addImageToGallery(testUser);
    EcatImageAnnotation an2 =
        postSaveImageAnnotation(annot, base64Image, img2.getId(), field.getId());
    annotationLink = richTextUpdater.generateAnnotatedImageElement(an2, field.getId() + "");
    String currContent = fieldMgr.get(field.getId(), testUser).get().getFieldData();
    doAutosaveAndCancelMVC(field, currContent + " " + annotationLink, testUser);

    String revisionNumber = getRevisionNumberofAnnotation(field);
    // this shouldn't throw an exception
    mockMvc
        .perform(
            getAnnotatedImageURL(ann)
                .param("revision", revisionNumber)
                .principal(new MockPrincipal(testUser.getUsername())))
        .andReturn();
  }

  private String getRevisionNumberofAnnotation(final Field fld) {
    String currContent;
    currContent = fieldMgr.get(fld.getId(), testUser).get().getFieldData();
    Pattern revision = Pattern.compile("revision=(\\d+)");
    Matcher m = revision.matcher(currContent);
    assertTrue(m.find());
    String revisionNumber = m.group(1);
    return revisionNumber;
  }

  EcatImageAnnotation postSaveImageAnnotation(String annot, String base64, Long imgId, Long fieldId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                getSaveImageAnnotationURL()
                    .param("annotations", annot)
                    .param("image", base64)
                    .param("imageId", imgId + "")
                    .param("parentId", fieldId + "")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    EcatImageAnnotation annotObject =
        getFromJsonAjaxReturnObject(result, EcatImageAnnotation.class);
    return annotObject;
  }

  @Test
  public void saveLoadImageAnnotation() throws Exception {

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    final Field fld = doc.getFields().get(0);
    EcatImageAnnotation ann = addImageAnnotationToField(fld, testUser);

    // check getting annotated image through getImage url (used before 1.28)
    byte[] dataOldUrl =
        mockMvc
            .perform(getImageURL(fld, ann).principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    // check getting annotated image through getAnnotation url (used from 1.29)
    byte[] dataNewUrl =
        mockMvc
            .perform(getAnnotatedImageURL(ann).principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertArrayEquals(dataOldUrl, dataNewUrl);
    Image fullImage = getImageFromBytes(dataNewUrl);

    // expected width of annotated image, asserts that it was created OK
    assertEquals(171, fullImage.getWidth(null));

    // now lets try getting the annotation as a string:
    MvcResult result3 =
        mockMvc
            .perform(
                getImageAnnotationStringURL(fld, ann.getImageId())
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    Map datajsonString = parseJSONObjectFromResponseStream(result3);
    assertTrue("annotation string was empty", ((String) datajsonString.get("data")).length() > 0);

    // now lets login as another user, this should be forbidden
    logoutAndLoginAs(other);
    result3 =
        mockMvc
            .perform(
                getImageAnnotationStringURL(fld, ann.getImageId())
                    .principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assertAuthorizationException(result3);

    MvcResult result =
        mockMvc
            .perform(getImageURL(fld, ann).principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assertAuthorizationException(result);
  }

  MockHttpServletRequestBuilder getImageAnnotationStringURL(Field fld, Long id) {
    return get("/image/ajax/loadImageAnnotations")
        .param("parentId", fld.getId() + "")
        .param("imageId", id + "");
  }

  MockHttpServletRequestBuilder getSketchImageAnnotationStringURL(Field fld, Long id) {
    return get("/image/ajax/loadSketchImageAnnotations").param("sketchId", id + "");
  }

  MockHttpServletRequestBuilder getImageURL(Field fld, EcatImageAnnotation ann) {
    return get("/image/getImage/{id}/{unused}", fld.getId() + "-" + ann.getImageId(), "1234");
  }

  MockHttpServletRequestBuilder getAnnotatedImageURL(EcatImageAnnotation ann) {
    return get("/image/getAnnotation/{id}/{unused}", ann.getId(), "1234");
  }

  MockHttpServletRequestBuilder getSketchURL(Integer id) {
    return get("/image/getImageSketch/{id}/{unused}", id, "1234");
  }

  MockHttpServletRequestBuilder getSaveImageAnnotationURL() {
    return post("/image/ajax/saveImageAnnotation");
  }

  @Test
  public void testGetAlreadySetThumbnail() throws Exception {
    byte[] b = testThumbnail(false);
    assertTrue("byte array was " + b.length, b.length > 0); // at least as long
  }

  @Test
  public void testGeneratedThumbnail() throws Exception {
    byte[] b = testThumbnail(true);
    assertTrue("byte array was " + b.length, b.length > 0); // at least as long
  }

  @Test
  public void testGeneratedThumbnailFromPDF() throws Exception {
    StructuredDocument dpc = createBasicDocumentInRootFolderWithText(testUser, "any");
    EcatDocumentFile docFile =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyPdf(), dpc.getFields().get(0), testUser);
    MvcResult res = doGetThumbnail(docFile, testUser);
    byte[] b = res.getResponse().getContentAsByteArray();
    assertEquals(HttpStatus.OK.value(), res.getResponse().getStatus());
    assertTrue("byte array was " + b.length, b.length > 0); // at least as long
  }

  @Test
  public void testGetUnsupportedThumbnailReturnsDefault() throws Exception {
    logoutAndLoginAs(testUser);
    final long id = new Long(RandomStringUtils.randomNumeric(8));
    EcatDocumentFile doc = TestFactory.createEcatDocument(id, testUser);
    // excel currently not supported, so should default icon not a 404
    doc.setName("document.xls");
    doc.setContentType("application/msexcel");
    doc.setOwner(piUser);
    folderMgr.addChild(getRootFolderForUser(testUser).getId(), doc, testUser);
    MvcResult res = doGetThumbnail(doc, testUser);
    assertEquals(HttpStatus.OK.value(), res.getResponse().getStatus());
  }

  private MvcResult doGetThumbnail(EcatDocumentFile doc, User user) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/image/docThumbnail/{id}/0", doc.getId())
                    .principal(new MockPrincipal(user.getUsername())))
            .andReturn();
    return res;
  }

  private byte[] testThumbnail(boolean isGenerated) throws Exception {
    logoutAndLoginAs(testUser);
    final long id = new Long(RandomStringUtils.randomNumeric(8));
    EcatDocumentFile doc = TestFactory.createEcatDocument(id, testUser);
    doc.setOwner(piUser);
    if (isGenerated) {
      InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("tester.png");
      byte[] data = IOUtils.toByteArray(is);
      doc.setThumbNail(new ImageBlob(data));
    }
    folderMgr.addChild(getRootFolderForUser(testUser).getId(), doc, testUser);
    MvcResult res = doGetThumbnail(doc, testUser);
    assertNull("exception thrown :" + res.getResolvedException(), res.getResolvedException());
    byte[] b = res.getResponse().getContentAsByteArray();
    return b;
  }

  @SuppressWarnings("unchecked")
  @Test
  public void saveLoadSketch() throws Exception {
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(testUser, "any");
    Long fId = sd.getFields().get(0).getId();
    String base64 = getBase64Image();
    mockPrincipal = new MockPrincipal(testUser.getUsername());
    MvcResult result =
        mockMvc
            .perform(
                post("/image/ajax/saveSketch")
                    .param("image", base64)
                    .param("sketchId", "")
                    .param("fieldId", fId + "")
                    .param("annotations", "any annotation")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    Map<String, Object> json = parseJSONObjectFromResponseStream(result);
    assertNotNull(json.get("data"));
    Map<String, Object> data = (Map<String, Object>) json.get("data");
    Integer id = (Integer) data.get("id");
    assertNotNull(id);
    assertNull(data.get("data")); // image data not returned, not needed.

    // now check that unaith user throws AuthException:
    logoutAndLoginAs(other);
    mockPrincipal = new MockPrincipal(other.getUsername());
    result =
        mockMvc
            .perform(
                post("/image/ajax/saveSketch")
                    .param("image", base64)
                    .param("sketchId", "")
                    .param("fieldId", fId + "")
                    .param("annotations", "any annotation")
                    .principal(mockPrincipal))
            .andReturn();
    assertAuthorizationException(result);
    logoutAndLoginAs(testUser);
    mockPrincipal = new MockPrincipal(testUser.getUsername());

    // now retrieve sketch
    assertNotNull(
        mockMvc
            .perform(getSketchURL(id).principal(new MockPrincipal(testUser.getUsername())))
            .andReturn()
            .getResponse()
            .getContentAsByteArray());

    // auth check, other user can't access
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    Field fld = doc.getFields().get(0);
    EcatImageAnnotation sketch = addSketchToField(fld, testUser);
    logoutAndLoginAs(other);
    result =
        mockMvc
            .perform(
                getSketchURL(sketch.getId().intValue())
                    .principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assertAuthorizationException(result);

    // check can't get annotation string either
    result = mockMvc.perform(getSketchImageAnnotationStringURL(fld, sketch.getId())).andReturn();
    assertAuthorizationException(result);
  }
}
