package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@TestPropertySource(
    properties = {"sample.png=src/test/resources/TestResources/GalleryThumbnail54x76.png"})
public class ImageControllerMVCIT extends MVCTestBase {

  private User testUser, other;
  private @Autowired EcatImageAnnotationDao imageAnnotationDao;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    final int n = 10;
    testUser = createAndSaveUser(getRandomName(n));
    other = createAndSaveUser(getRandomName(n));
    initUsers(testUser, other);
    logoutAndLoginAs(testUser);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
    RSpaceTestUtils.logout();
  }

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
                multipart("/gallery/ajax/uploadFile")
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
    assertThat(imageInfoList).hasSize(1);

    Map imageInfo = imageInfoList.get(0);
    assertEquals(image.getId() + "", imageInfo.get("id") + "");
    assertThat(imageInfo).containsEntry("width", image.getWidth());
    assertThat(imageInfo).containsEntry("height", image.getHeight());
    assertThat(imageInfo).containsEntry("name", image.getName());
    assertThat(imageInfo).containsEntry("description", image.getDescription());
  }

  private MockHttpServletRequestBuilder getImageToAnnotate(final Field fld, EcatImage image) {
    return get("/image/getImageToAnnotate/{id}/{unused}", fld.getId() + "-" + image.getId(), "any");
  }

  @Test
  public void galleryListsSharedDocumentAfterImageIsAnnotatedBetweenAutosaves() throws Exception {
    Group group = createGroupForUsersWithDefaultPi(testUser, other);
    logoutAndLoginAs(testUser);
    StructuredDocument document = createBasicDocumentInRootFolderWithText(testUser, "");
    Field field = document.getFields().get(0);
    EcatImage source = addImageToGallery(testUser);
    doAutosaveMVC(
        field, richTextUpdater.generateRawImageElement(source, field.getId().toString()), testUser);

    EcatImageAnnotation annotation =
        postSaveImageAnnotation(
            RSpaceTestUtils.getAnyAnnotationOrSketchJson(),
            getImageBytes(),
            source.getId(),
            field.getId());
    String annotatedImage =
        richTextUpdater.generateAnnotatedImageElement(annotation, field.getId().toString());
    doAutosaveAndSaveMVC(field, annotatedImage, testUser);
    shareRecordWithGroup(testUser, group, document);

    mockMvc
        .perform(
            get("/workspace/getRecordInformation")
                .param("recordId", source.getId().toString())
                .principal(new MockPrincipal(testUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value(source.getName()));
    mockMvc
        .perform(
            get("/gallery/ajax/getLinkedDocuments/{mediaId}", source.getId())
                .principal(new MockPrincipal(testUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(document.getId().intValue()));

    List<FieldAttachment> sourceLinks =
        fieldMgr.getFieldAttachments(field.getId()).stream()
            .filter(link -> source.getId().equals(link.getMediaFile().getId()))
            .toList();
    assertEquals(1, sourceLinks.size());
    assertFalse(sourceLinks.get(0).isDeleted());

    logoutAndLoginAs(other);
    mockMvc
        .perform(getAnnotatedImageURL(annotation).principal(new MockPrincipal(other.getUsername())))
        .andExpect(status().isOk());
  }

  @Test
  public void annotatingOneImageWhileRemovingAnotherUnlinksOnlyTheRemovedImage() throws Exception {
    logoutAndLoginAs(testUser);
    StructuredDocument document = createBasicDocumentInRootFolderWithText(testUser, "");
    Field field = document.getFields().get(0);
    String fieldId = field.getId().toString();
    EcatImage annotated = addImageToGallery(testUser);
    EcatImage removed = addImageToGallery(testUser);
    doAutosaveAndSaveMVC(
        field,
        richTextUpdater.generateRawImageElement(annotated, fieldId)
            + richTextUpdater.generateRawImageElement(removed, fieldId),
        testUser);

    EcatImageAnnotation annotation =
        postSaveImageAnnotation(
            RSpaceTestUtils.getAnyAnnotationOrSketchJson(),
            getImageBytes(),
            annotated.getId(),
            field.getId());
    doAutosaveMVC(
        field, richTextUpdater.generateAnnotatedImageElement(annotation, fieldId), testUser);

    mockMvc
        .perform(
            get("/gallery/ajax/getLinkedDocuments/{mediaId}", annotated.getId())
                .principal(new MockPrincipal(testUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(document.getId().intValue()));
    mockMvc
        .perform(
            get("/gallery/ajax/getLinkedDocuments/{mediaId}", removed.getId())
                .principal(new MockPrincipal(testUser.getUsername())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));

    // the removed image was in the saved field, so its link is kept for revision history
    List<FieldAttachment> links = fieldMgr.getFieldAttachments(field.getId());
    assertFalse(linkTo(links, annotated).isDeleted());
    assertTrue(linkTo(links, removed).isDeleted());
  }

  private FieldAttachment linkTo(List<FieldAttachment> links, EcatImage image) {
    List<FieldAttachment> matching =
        links.stream().filter(link -> image.getId().equals(link.getMediaFile().getId())).toList();
    assertEquals(1, matching.size());
    return matching.get(0);
  }

  @Test
  public void loadRevisionAfterCancelRSPAC_939() throws Exception {
    // follow steps of this bug report and check revision can be retrieved
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(testUser, "any");
    final Field field = doc.getFields().get(0);
    byte[] image = getImageBytes();
    // create 2nd annotation, autosave and do real save
    String annot = RSpaceTestUtils.getAnyAnnotationOrSketchJson();
    EcatImage img = addImageToGallery(testUser);

    EcatImageAnnotation ann = postSaveImageAnnotation(annot, image, img.getId(), field.getId());
    String annotationLink = richTextUpdater.generateAnnotatedImageElement(ann, field.getId() + "");
    doAutosaveAndSaveMVC(field, annotationLink, testUser);

    // create 2nd annotation, autosave and cancel
    EcatImage img2 = addImageToGallery(testUser);
    EcatImageAnnotation an2 = postSaveImageAnnotation(annot, image, img2.getId(), field.getId());
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

  EcatImageAnnotation postSaveImageAnnotation(String annot, byte[] image, Long imgId, Long fieldId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                getSaveImageAnnotationURL()
                    .file(imagePart(image))
                    .param("annotations", annot)
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

    assertThat(dataNewUrl).containsExactly(dataOldUrl);
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
    assertTrue(((String) datajsonString.get("data")).length() > 0, "annotation string was empty");

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

  @Test
  public void saveImageAnnotationStoresUploadedPngUnchanged() throws Exception {
    Field field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Long imageId = addImageToGallery(testUser).getId();
    byte[] png = noisyPng(400, 400);
    assertThat(png.length).isGreaterThan(200_000);

    EcatImageAnnotation saved = postSaveImageAnnotation("large", png, imageId, field.getId());

    assertEquals(400, saved.getWidth());
    assertArrayEquals(png, doInTransaction(() -> imageAnnotationDao.get(saved.getId()).getData()));
  }

  @Test
  public void savesRejectEmptyOrUnreadableImagesAndKeepThePreviousImage() throws Exception {
    Field field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Long imageId = addImageToGallery(testUser).getId();
    byte[] original = getImageBytes();
    Long annotationId =
        postSaveImageAnnotation("original", original, imageId, field.getId()).getId();

    byte[] notAnImage = "not a png".getBytes(StandardCharsets.UTF_8);
    assertInstanceOf(
        IllegalArgumentException.class,
        saveImageAnnotationAs(testUser, field.getId(), imageId, annotationId, "changed", notAnImage)
            .getResolvedException());
    assertInstanceOf(
        IllegalArgumentException.class,
        saveImageAnnotationAs(testUser, field.getId(), imageId, null, "new", new byte[0])
            .getResolvedException());
    MvcResult emptySketch =
        mockMvc
            .perform(
                multipart("/image/ajax/saveSketch")
                    .file(imagePart(new byte[0]))
                    .param("sketchId", "")
                    .param("fieldId", field.getId() + "")
                    .param("annotations", "any annotation")
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andReturn();
    assertInstanceOf(IllegalArgumentException.class, emptySketch.getResolvedException());

    assertEquals("original", getAnnotations(annotationId));
    assertArrayEquals(
        original, doInTransaction(() -> imageAnnotationDao.get(annotationId).getData()));
    assertThat(getAnnotationIdsInField(field.getId())).containsExactly(annotationId);
  }

  private byte[] noisyPng(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Random random = new Random(1);
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        image.setRGB(x, y, random.nextInt(0x1000000));
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  @Test
  public void saveImageAnnotationKeepsEachImageOccurrenceSeparate() throws Exception {
    Field field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Long imageId = addImageToGallery(testUser).getId();
    byte[] image = getImageBytes();

    EcatImageAnnotation first = postSaveImageAnnotation("first", image, imageId, field.getId());
    EcatImageAnnotation second = postSaveImageAnnotation("second", image, imageId, field.getId());
    assertNotEquals(first.getId(), second.getId());

    EcatImageAnnotation updated =
        getFromJsonAjaxReturnObject(
            saveImageAnnotationAs(testUser, field.getId(), imageId, second.getId(), "updated"),
            EcatImageAnnotation.class);
    assertEquals(second.getId(), updated.getId());
    assertEquals("first", getAnnotations(first.getId()));
    assertEquals("updated", getAnnotations(second.getId()));
    assertThat(getAnnotationIdsInField(field.getId()))
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  public void saveImageAnnotationRejectsMismatchedAnnotationOrMissingWritePermission()
      throws Exception {
    Field field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Field otherField = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Long imageId = addImageToGallery(testUser).getId();
    Long otherImageId = addImageToGallery(testUser).getId();
    Long annotationId =
        postSaveImageAnnotation("original", getImageBytes(), imageId, field.getId()).getId();

    assertAuthorizationException(
        saveImageAnnotationAs(testUser, otherField.getId(), imageId, annotationId, "changed"));
    assertAuthorizationException(
        saveImageAnnotationAs(testUser, field.getId(), otherImageId, annotationId, "changed"));
    assertInstanceOf(
        ObjectRetrievalFailureException.class,
        saveImageAnnotationAs(testUser, field.getId(), imageId, annotationId + 1000, "changed")
            .getResolvedException());

    logoutAndLoginAs(other);
    assertAuthorizationException(
        saveImageAnnotationAs(other, field.getId(), imageId, null, "changed"));
    assertAuthorizationException(
        saveImageAnnotationAs(other, field.getId(), imageId, annotationId, "changed"));
    logoutAndLoginAs(testUser);

    assertEquals("original", getAnnotations(annotationId));
    assertThat(getAnnotationIdsInField(field.getId())).containsExactly(annotationId);
    assertThat(getAnnotationIdsInField(otherField.getId())).isEmpty();
  }

  @Test
  public void legacyImageLinkLoadsTheOldestAnnotationForItsFieldAndImage() throws Exception {
    Field field = createBasicDocumentInRootFolderWithText(testUser, "any").getFields().get(0);
    Long imageId = addImageToGallery(testUser).getId();
    byte[] image = getImageBytes();

    assertEquals("", getLegacyLinkAnnotations(field, imageId));
    postSaveImageAnnotation("oldest", image, imageId, field.getId());
    assertEquals("oldest", getLegacyLinkAnnotations(field, imageId));
    postSaveImageAnnotation("newer", image, imageId, field.getId());
    assertEquals("oldest", getLegacyLinkAnnotations(field, imageId));
  }

  private MvcResult saveImageAnnotationAs(
      User user, Long fieldId, Long imageId, Long annotationId, String annotations)
      throws Exception {
    return saveImageAnnotationAs(
        user, fieldId, imageId, annotationId, annotations, getImageBytes());
  }

  private MvcResult saveImageAnnotationAs(
      User user, Long fieldId, Long imageId, Long annotationId, String annotations, byte[] png)
      throws Exception {
    MockHttpServletRequestBuilder request =
        getSaveImageAnnotationURL()
            .file(imagePart(png))
            .param("annotations", annotations)
            .param("imageId", imageId + "")
            .param("parentId", fieldId + "")
            .principal(new MockPrincipal(user.getUsername()));
    if (annotationId != null) {
      request.param("annotationId", annotationId + "");
    }
    return mockMvc.perform(request).andReturn();
  }

  private String getAnnotations(Long annotationId) throws Exception {
    return doInTransaction(() -> imageAnnotationDao.get(annotationId).getAnnotations());
  }

  private List<Long> getAnnotationIdsInField(Long fieldId) throws Exception {
    return doInTransaction(
        () ->
            imageAnnotationDao.getAllImageAnnotationsFromField(fieldId).stream()
                .map(EcatImageAnnotation::getId)
                .toList());
  }

  private String getLegacyLinkAnnotations(Field field, Long imageId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                getImageAnnotationStringURL(field, imageId)
                    .principal(new MockPrincipal(testUser.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    return (String) parseJSONObjectFromResponseStream(result).get("data");
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

  MockMultipartHttpServletRequestBuilder getSaveImageAnnotationURL() {
    return multipart("/image/ajax/saveImageAnnotation");
  }

  private MockMultipartFile imagePart(byte[] png) {
    return new MockMultipartFile("image", "sketch.png", MediaType.IMAGE_PNG_VALUE, png);
  }

  @Test
  public void testGetAlreadySetThumbnail() throws Exception {
    byte[] b = testThumbnail(false);
    assertTrue(b.length > 0, "byte array was " + b.length);
  }

  @Test
  public void testGeneratedThumbnail() throws Exception {
    byte[] b = testThumbnail(true);
    assertTrue(b.length > 0, "byte array was " + b.length);
  }

  @Test
  public void testGeneratedThumbnailFromPDF() throws Exception {
    StructuredDocument dpc = createBasicDocumentInRootFolderWithText(testUser, "any");
    EcatDocumentFile docFile =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyPdf(), dpc.getFields().get(0), testUser);
    MvcResult res = doGetThumbnail(docFile, testUser);
    byte[] b = res.getResponse().getContentAsByteArray();
    assertEquals(HttpStatus.OK.value(), res.getResponse().getStatus());
    assertTrue(b.length > 0, "byte array was " + b.length);
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
    assertNull(res.getResolvedException(), "exception thrown :" + res.getResolvedException());
    byte[] b = res.getResponse().getContentAsByteArray();
    return b;
  }

  @Test
  public void saveLoadSketch() throws Exception {
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(testUser, "any");
    Long fId = sd.getFields().get(0).getId();
    byte[] image = getImageBytes();
    mockPrincipal = new MockPrincipal(testUser.getUsername());
    MvcResult result =
        mockMvc
            .perform(
                multipart("/image/ajax/saveSketch")
                    .file(imagePart(image))
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
                multipart("/image/ajax/saveSketch")
                    .file(imagePart(image))
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
