package com.researchspace.webapp.controller;

import static com.researchspace.core.util.MediaUtils.CHEMISTRY_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.DOCUMENT_MEDIA_FLDER_NAME;
import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static com.researchspace.testutils.RSpaceTestUtils.getAnyPdf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.http.entity.ContentType;
import org.hibernate.criterion.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.ui.ModelMap;

@TestPropertySource(properties = "chemistry.web.url=http://howler.researchspace.com:8099")
public class GalleryControllerMVCIT extends MVCTestBase {

  private @Autowired GalleryController galleryController;
  private @Autowired RecordManager recordManager;
  private @Autowired MediaManager mediaManager;
  private @Autowired RSChemElementManager rsChemElementManager;
  public @Rule TemporaryFolder tempIndexFolder = new TemporaryFolder();
  @Autowired FileIndexSearcher searcher;

  private User owner;
  private PaginationCriteria<BaseRecord> pgcrit = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    owner = createInitAndLoginAnyUser();
    pgcrit = PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testOpeningGalleryOnRootFolder() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(String.format("%s", GalleryController.GALLERY_URL))
                    .principal(owner::getUsername))
            .andExpect(status().isOk())
            .andReturn();
    ModelMap modelMap = result.getModelAndView().getModelMap();
    assertNotNull(modelMap.get("groups"));
    assertNotNull(modelMap.get("uniqueUsers"));
    assertNull(result.getResolvedException());
  }

  @Test
  public void galleryDialogTest() throws Exception {
    assertNotNull(
        mockMvc
            .perform(get("/gallery/ajax/galleryDialog").principal(owner::getUsername))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  public void testOpeningGalleryOnSpecificFolder() throws Exception {
    Long audioFolderId =
        recordManager.getGallerySubFolderForUser(MediaUtils.AUDIO_MEDIA_FLDER_NAME, owner).getId();
    MvcResult result =
        mockMvc
            .perform(
                get(String.format("%s/%d", GalleryController.GALLERY_URL, audioFolderId))
                    .principal(owner::getUsername))
            .andExpect(status().isOk())
            .andReturn();
    ModelMap modelMap = result.getModelAndView().getModelMap();
    assertEquals(audioFolderId, modelMap.get("currentFolderId"));
    assertEquals(MediaUtils.AUDIO_MEDIA_FLDER_NAME, modelMap.get("mediaType"));
    assertNull(result.getResolvedException());
  }

  @Test
  public void testOpeningGalleryOnSpecificFolderFromItem() throws Exception {
    RecordInformation imageInfo = uploadImageToGallery();
    Long imageFolderId =
        recordManager.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, owner).getId();
    MvcResult result =
        mockMvc
            .perform(
                get(String.format("%s/%d", GalleryController.GALLERY_ITEM_URL, imageInfo.getId()))
                    .principal(owner::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    ModelMap modelMap = result.getModelAndView().getModelMap();
    assertEquals(imageFolderId, modelMap.get("currentFolderId"));
    assertEquals(MediaUtils.IMAGES_MEDIA_FLDER_NAME, modelMap.get("mediaType"));
    assertNull(result.getResolvedException());
  }

  @Test
  public void uploadToTargetFolder_RSPAC_1703() throws Exception {
    Folder imagesFolder = recordManager.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, owner);
    final long initialCountInImagesFolder = getRecordCountInFolderForUser(imagesFolder.getId());
    Folder target = createSubFolder(imagesFolder, "imagesTarget", owner);
    assertEquals(0, getRecordCountInFolderForUser(target.getId()));
    RecordInformation uploaded = uploadImageToGallery(target.getId());
    assertNotNull(uploaded.getId());
    assertEquals(1, getRecordCountInFolderForUser(target.getId()));

    // this is folder in wrong part of gallery for image file
    Folder docFolder = recordManager.getGallerySubFolderForUser(DOCUMENT_MEDIA_FLDER_NAME, owner);
    RecordInformation uploaded2 = uploadImageToGallery(docFolder.getId());
    assertNotNull(uploaded2.getId());
    // it gets uploaded to image root folder instead
    assertEquals(1, getRecordCountInFolderForUser(target.getId()));
    // folder + 2nd image
    assertEquals(
        initialCountInImagesFolder + 2, getRecordCountInFolderForUser(imagesFolder.getId()));
  }

  @Test
  public void testAccessExceptionsOnOpeningGalleryOnSpecificFolder() throws Exception {

    // unexisting folder
    MvcResult unexistingResult =
        mockMvc.perform(get("/gallery/0").principal(owner::getUsername)).andReturn();

    Exception unexistingException = unexistingResult.getResolvedException();
    assertNotNull(unexistingException);
    assertEquals("access denied", unexistingException.getMessage());

    // other user's images folder
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(other);
    Folder otherUsersImagesFolder =
        recordManager.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, other);
    MvcResult otherUsersFolderResult =
        mockMvc
            .perform(
                get("/gallery/" + otherUsersImagesFolder.getId()).principal(owner::getUsername))
            .andReturn();

    Exception otherUsersFolderException = otherUsersFolderResult.getResolvedException();
    assertNotNull(otherUsersFolderException);
    assertEquals("access denied", otherUsersFolderException.getMessage());

    // folder outside othe Gallery
    Folder usersRootFolder = getRootFolderForUser(owner);
    MvcResult notInGalleryResult =
        mockMvc
            .perform(get("/gallery/" + usersRootFolder.getId()).principal(owner::getUsername))
            .andReturn();

    Exception notInGalleryException = notInGalleryResult.getResolvedException();
    assertNotNull(notInGalleryException);
    assertEquals(
        "provided folderId doesn't point to Gallery folder", notInGalleryException.getMessage());
  }

  @Test
  public void testGetUploadedFilesFilter() throws Exception {

    // now order by name, should be OK
    AjaxReturnObject<GalleryData> data =
        galleryController.getUploadedFiles(MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(data.getData());

    EcatImage image1 = addImageToGallery(owner);
    String newname = getRandomAlphabeticString("image");
    recordMgr.renameRecord(newname, image1.getId(), owner);
    data =
        galleryController.getUploadedFiles(
            MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, new GalleryFilterCriteria(newname));
    assertEquals(1, data.getData().getItems().getHits().intValue());
  }

  @Test
  public void testGetUploadedFiles() throws Exception {
    // initialiseFileIndexer(false);
    initialiseIndexFolder();
    User subject = createInitAndLoginAnyUser();
    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(res.getData());

    final int initialImgSize = res.getData().getItems().getHits();

    // assertEquals(4,INITIAL_SIZE);// 3 images + shared folders
    uploadImageToGallery();

    AjaxReturnObject<GalleryData> res2 =
        galleryController.getUploadedFiles(MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertEquals(
        initialImgSize + 1,
        res2.getData().getItems().getHits().intValue()); // 3 images + shared folder
    assertTrue(res2.getData().isOnRoot());

    // Assert unknown file format goes to MiscelleaneousDocument Folder
    AjaxReturnObject<GalleryData> miscResult =
        galleryController.getUploadedFiles(MediaUtils.MISC_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(miscResult.getData());
    assertTrue(miscResult.getData().isOnRoot());

    final int initialMiscSize = miscResult.getData().getItems().getHits();

    MockMultipartFile mu =
        new MockMultipartFile("file.sh", "file.sh", "sh", getTestResourceFileStream("file.sh"));

    // now we simulate associate with a field (Via Dnd into tinymce)
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(subject, "any");
    Field field = sd.getFields().get(0);
    galleryController.uploadFile(mu, null, null, field.getId());

    AjaxReturnObject<GalleryData> miscResult2 =
        galleryController.getUploadedFiles(MediaUtils.MISC_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertEquals(
        initialMiscSize + 1,
        miscResult2.getData().getItems().getHits().intValue()); // 1 file + shared folder
    assertTrue(miscResult.getData().isOnRoot());
    // and assert that association is made. this is lazyloaded so we need
    // to run in in a session
    openTransaction();
    Field updated = fieldMgr.get(field.getId(), subject).get();
    int numAttachments = updated.getLinkedMediaFiles().size();
    commitTransaction(); // commit before assertion in case of failure
    assertEquals(1, numAttachments);

    AjaxReturnObject<GalleryData> audioCount =
        galleryController.getUploadedFiles(MediaUtils.AUDIO_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(audioCount.getData());
    final int initialAudioSize = audioCount.getData().getItems().getHits();

    // now we'll upload an audio file and test it appears in the audio media folder
    MockMultipartFile mfAudio =
        new MockMultipartFile(
            "mpthreetest.mp3",
            "mpthreetest.mp3",
            "mp3",
            getTestResourceFileStream("mpthreetest.mp3"));

    galleryController.uploadFile(mfAudio, null, null, null);
    AjaxReturnObject<GalleryData> res3 =
        galleryController.getUploadedFiles(MediaUtils.AUDIO_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertEquals(initialAudioSize + 1, res3.getData().getItems().getHits().intValue());

    Long audioFileId = assertAudioFileAdded(res3);

    // let's move audio to the subfolder
    Folder audioFolder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.AUDIO_MEDIA_FLDER_NAME, subject);
    final String AUDIOSUBFOLDER_NAME = "subFolderOfAudio";
    Folder newSubfolder = createSubFolder(audioFolder, AUDIOSUBFOLDER_NAME, subject);

    AjaxReturnObject<Boolean> rc =
        galleryController.moveGalleriesElements(
            new Long[] {audioFileId}, MediaUtils.AUDIO_MEDIA_FLDER_NAME, newSubfolder.getId());
    assertTrue(rc.getData());

    // move the audio to the Root folder
    rc =
        galleryController.moveGalleriesElements(
            new Long[] {audioFileId}, MediaUtils.AUDIO_MEDIA_FLDER_NAME, 0L);
    assertTrue(rc.getData());

    /* one more check, let's ensure the controller doesn't allow selectedMediaId and fieldId
     * parameters at the same time - which is not expected, as we either upload new version
     * of existing media/attachment, or attach completely new file to the field */
    final Long audioId = audioFileId;
    assertExceptionThrown(
        () -> galleryController.uploadFile(mfAudio, audioId, null, field.getId()),
        IllegalArgumentException.class);
  }

  private void initialiseIndexFolder() throws IOException, Exception {
    fileIndexer = new FileIndexer();
    fileIndexer.setIndexFolderDirectly(tempIndexFolder.getRoot());
    fileIndexer.init(true);
    getTargetObject(searcher.getFileSearchStrategy(), LuceneSearchStrategy.class)
        .setIndexFolderDirectly(tempIndexFolder.getRoot());
  }

  private Long assertAudioFileAdded(AjaxReturnObject<GalleryData> res3) {
    Long audioFileId = null;
    for (RecordInformation ri : res3.getData().getItems().getResults()) {
      if (ri.getName().contains("mpthreetest")) {
        audioFileId = ri.getId();
      }
    }
    assertNotNull(audioFileId);
    return audioFileId;
  }

  @Test
  public void uploadNewVersionOfGalleryFile() throws Exception {
    User user = createInitAndLoginAnyUser();
    mockPrincipal = user::getUsername;

    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(res.getData());
    final int initialImgCount = res.getData().getItems().getHits().intValue();

    // upload first picture file
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "Picture1.png", "png", getTestResourceFileStream("Picture1.png"));
    MvcResult result =
        mockMvc
            .perform(fileUpload("/gallery/ajax/uploadFile").file(mf).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    RecordInformation imageInfo = getFromJsonAjaxReturnObject(result, RecordInformation.class);
    assertNotNull(imageInfo);
    assertEquals(1, imageInfo.getVersion());

    // upload second picture file, but for the same id
    MockMultipartFile mf2 =
        new MockMultipartFile(
            "xfile", "Picture2.png", "png", getTestResourceFileStream("Picture2.png"));
    MvcResult result2 =
        mockMvc
            .perform(
                fileUpload("/gallery/ajax/uploadFile")
                    .file(mf2)
                    .param("selectedMediaId", "" + imageInfo.getId())
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    RecordInformation updatedImageInfo =
        getFromJsonAjaxReturnObject(result2, RecordInformation.class);
    assertNotNull(updatedImageInfo);

    // check updated media file details
    assertEquals(imageInfo.getId(), updatedImageInfo.getId());
    assertEquals(imageInfo.getOriginalFileName(), updatedImageInfo.getOriginalFileName());
    assertNotEquals(imageInfo.getSize(), updatedImageInfo.getSize());
    assertEquals(2, updatedImageInfo.getVersion());

    // ensure just one new file in the gallery
    AjaxReturnObject<GalleryData> res2 =
        galleryController.getUploadedFiles(MediaUtils.IMAGES_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(res2.getData());
    final int finalImgSize = res2.getData().getItems().getHits().intValue();
    assertEquals(initialImgCount + 1, finalImgSize);
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testChemistryFileUploadNewVersion() throws Exception {
    User user = createInitAndLoginAnyUser();
    mockPrincipal = user::getUsername;

    AjaxReturnObject<GalleryData> res =
        galleryController.getUploadedFiles(CHEMISTRY_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(res.getData());
    final int initialChemCount = res.getData().getItems().getHits();

    // upload first picture file
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile",
            "Amfetamine.mol",
            ContentType.DEFAULT_BINARY.getMimeType(),
            getTestResourceFileStream("Amfetamine.mol"));
    MvcResult result =
        mockMvc
            .perform(fileUpload("/gallery/ajax/uploadFile").file(mf).principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    RecordInformation chemInfo = getFromJsonAjaxReturnObject(result, RecordInformation.class);
    assertNotNull(chemInfo);
    assertEquals(1, chemInfo.getVersion());

    List<RSChemElement> chemElements =
        rsChemElementManager.getRSChemElementsLinkedToFile(chemInfo.getId(), user);
    assertEquals(1, chemElements.size());

    // upload second picture file, but for the same id
    MockMultipartFile mf2 =
        new MockMultipartFile(
            "xfile",
            "Aminoglutethimide.mol",
            ContentType.DEFAULT_BINARY.getMimeType(),
            getTestResourceFileStream("Aminoglutethimide.mol"));
    MvcResult result2 =
        mockMvc
            .perform(
                fileUpload("/gallery/ajax/uploadFile")
                    .file(mf2)
                    .param("selectedMediaId", "" + chemInfo.getId())
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    RecordInformation updatedChemInfo =
        getFromJsonAjaxReturnObject(result2, RecordInformation.class);
    assertNotNull(updatedChemInfo);

    List<RSChemElement> chemsAfterNewVersion =
        rsChemElementManager.getRSChemElementsLinkedToFile(chemInfo.getId(), user);
    assertEquals(1, chemsAfterNewVersion.size());

    // check updated media file details
    assertEquals(chemInfo.getId(), updatedChemInfo.getId());
    assertEquals(chemInfo.getOriginalFileName(), updatedChemInfo.getOriginalFileName());
    assertNotEquals(chemInfo.getSize(), updatedChemInfo.getSize());
    assertEquals(2, updatedChemInfo.getVersion());

    // ensure just one new file in the gallery
    AjaxReturnObject<GalleryData> res2 =
        galleryController.getUploadedFiles(CHEMISTRY_MEDIA_FLDER_NAME, 0, pgcrit, null);
    assertNotNull(res2.getData());
    final int finalChemSize = res2.getData().getItems().getHits();
    assertEquals(initialChemCount + 1, finalChemSize);
  }

  private RecordInformation uploadImageToGallery()
      throws FileNotFoundException, IOException, URISyntaxException {
    return uploadImageToGallery(null);
  }

  private RecordInformation uploadImageToGallery(Long imageSubfolderId)
      throws FileNotFoundException, IOException, URISyntaxException {
    MockMultipartFile mf =
        new MockMultipartFile(
            "image.png", "image.png", "png", getTestResourceFileStream("Picture1.png"));

    AjaxReturnObject<RecordInformation> ri =
        galleryController.uploadFile(mf, null, imageSubfolderId, null);
    return ri.getData();
  }

  private RecordInformation uploadFileToGallery(String fileName, String contentType)
      throws FileNotFoundException, IOException, URISyntaxException {
    return uploadFileToGallery(null, fileName, contentType);
  }

  private RecordInformation uploadFileToGallery(
      Long subfolderId, String fileName, String contentType)
      throws FileNotFoundException, IOException, URISyntaxException {
    MockMultipartFile mf =
        new MockMultipartFile(fileName, fileName, contentType, getTestResourceFileStream(fileName));
    AjaxReturnObject<RecordInformation> ri =
        galleryController.uploadFile(mf, null, subfolderId, null);
    return ri.getData();
  }

  @Test
  public void viewThumbnail() throws Exception {
    RecordInformation imageInfo = uploadImageToGallery();
    byte[] data =
        mockMvc
            .perform(getViewThumbnail(imageInfo).principal(new MockPrincipal(owner.getUsername())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    BufferedImage image = getImageFromBytes(data);
    assertEquals(76, image.getWidth()); // checking data is not null, this is expected size
    // unauthorised can't view
    User other = createInitAndLoginAnyUser();
    MvcResult result =
        mockMvc
            .perform(getViewThumbnail(imageInfo).principal(new MockPrincipal(other.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    assertAuthorizationException(result);
  }

  @Test
  public void viewerImages() throws Exception {
    RecordInformation imageInfo = uploadImageToGallery();
    byte[] data =
        mockMvc
            .perform(getViewerImage(imageInfo).principal(owner::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    BufferedImage image = getImageFromBytes(data);
    System.err.println(image.getWidth());
    // unauthorised can't view
    User other = createInitAndLoginAnyUser();
    MvcResult result =
        mockMvc
            .perform(getViewerImage(imageInfo).principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assertAuthorizationException(result);
  }

  @Test
  public void createCopyAndDeleteSnippet_RSPAC_1270() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc =
        createBasicDocumentInRootFolderWithText(user, "some text to include in snippet");
    Snippet snippet = recordManager.createSnippet("testSnippet", "text", user);
    galleryController.copyGalleries(
        new Long[] {snippet.getId()}, new String[] {"copy"}, new MockPrincipal(user.getUsername()));
    Snippet copy = getMostRecentlyCreatedSnippet();
    CompositeRecordOperationResult result =
        recordDeletionMgr.deleteRecord(copy.getParent().getId(), copy.getId(), user);
    assertEquals(copy, result.getRecords().iterator().next());
  }

  @Test
  public void testCreateCopyDeleteOfFolderWithMediaFileRSPAC2559() throws Exception {
    User user = createInitAndLoginAnyUser();
    // Create folder
    Folder folder = doInTransaction(() -> createImgGallerySubfolder("test-folder", user));
    // Upload image to new folder
    EcatImage originalImage = addImageToGalleryFolder(folder, user);
    // Copy folder
    galleryController.copyGalleries(
        new Long[] {folder.getId()},
        new String[] {"test-folder_copy"},
        new MockPrincipal(user.getUsername()));
    Folder copiedFolder = recordManager.getGallerySubFolderForUser("test-folder_copy", user);
    CompositeRecordOperationResult result =
        recordDeletionMgr.deleteFolder(
            copiedFolder.getParent().getId(), copiedFolder.getId(), user);
    assertEquals(copiedFolder, result.getRecords().iterator().next());
  }

  @Test
  // this has to be in 'real transaction' tests so as to be able to add/remove field attachments
  // doing this in single transaction doesn't set associations properly //rspac 1089
  public void testGetLinkedRecords() throws Exception {
    User user = createInitAndLoginAnyUser();
    logoutAndLoginAs(user);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(getAnyPdf(), doc.getFields().get(0), user);
    assertEquals(1, mediaMgr.getIdsOfLinkedDocuments(attachment.getId()).size());
    Field updated = fieldMgr.getWithLoadedMediaLinks(doc.getFields().get(0).getId(), user).get();

    doInTransaction(
        () -> {
          removeAttachmentFromField(attachment, updated, user);
        });
    // don't show linked docs once link deleted from field
    assertEquals(0, mediaMgr.getIdsOfLinkedDocuments(attachment.getId()).size());

    EcatDocumentFile attachment2 =
        addAttachmentDocumentToField(
            RSpaceTestUtils.getAnyAttachment(), doc.getFields().get(0), user);

    assertEquals(1, mediaMgr.getIdsOfLinkedDocuments(attachment2.getId()).size());
    assertEquals(doc.getId(), mediaMgr.getIdsOfLinkedDocuments(attachment2.getId()).get(0).getId());

    // don't show deleted docs
    recordDeletionMgr.deleteRecord(doc.getParent().getId(), doc.getId(), user);
    assertEquals(0, mediaMgr.getIdsOfLinkedDocuments(attachment.getId()).size());
  }

  @Test
  public void getGalleryInfoForList() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(getAnyPdf(), doc.getFields().get(0), user);
    EcatDocumentFile attachment2 =
        addAttachmentDocumentToField(getAnyPdf(), doc.getFields().get(0), user);

    // check single info request
    MvcResult res =
        mockMvc
            .perform(
                get("/gallery/getMediaFileSummaryInfo/")
                    .param("id[]", attachment.getId() + "")
                    .param("revision[]", ""))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data." + attachment.getId()).exists())
            .andReturn();
    // check two infos request
    res =
        mockMvc
            .perform(
                get("/gallery/getMediaFileSummaryInfo/")
                    .param("id[]", attachment.getId() + "," + attachment2.getId())
                    .param("revision[]", ","))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data." + attachment.getId()).exists())
            .andExpect(jsonPath("$.data." + attachment2.getId()).exists())
            .andReturn();

    // handle empty arrays
    res =
        mockMvc
            .perform(
                get("/gallery/getMediaFileSummaryInfo/").param("id[]", "").param("revision[]", ""))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
            .andExpect(jsonPath("$.data").exists())
            .andReturn();
    // IAE  thrown if mismatched array sizes
    res =
        mockMvc
            .perform(
                get("/gallery/getMediaFileSummaryInfo/")
                    .param("id[]", attachment.getId() + "," + attachment2.getId())
                    .param("revision[]", ",,,,"))
            .andReturn();
    assertException(res, IllegalArgumentException.class);

    // non existent id + existing ID
    res =
        mockMvc
            .perform(
                get("/gallery/getMediaFileSummaryInfo/")
                    .param("id[]", -2L + "," + attachment2.getId())
                    .param("revision[]", ","))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data").isNotEmpty())
            .andExpect(jsonPath("$.error.errorMessages").isNotEmpty())
            .andReturn();
  }

  // Chemistry File Specific Tests
  // 1. Generic test, check file goes in correct "Chemistry" folder
  // 2. Check RsChemElement is generated with a chemId
  @Test
  public void testUploadingChemistryFile() throws IOException, URISyntaxException {
    Folder chemistryFolder =
        recordManager.getGallerySubFolderForUser(CHEMISTRY_MEDIA_FLDER_NAME, owner);
    assertEquals(0, getRecordCountInFolderForUser(chemistryFolder.getId()));
    RecordInformation uploaded = uploadFileToGallery("Amfetamine.mol", "application/octet-stream");
    EcatChemistryFile chemistryFile = chemistryFileManager.get(uploaded.getId(), owner);
    assertNotNull(uploaded.getId());
    assertEquals(1, getRecordCountInFolderForUser(chemistryFolder.getId()));
    assertNotNull(chemistryFile);
    assertNotNull(chemistryFile.getChemString());
  }

  private Snippet getMostRecentlyCreatedSnippet() throws Exception {
    return doInTransaction(
        () ->
            (Snippet)
                sessionFactory
                    .getCurrentSession()
                    .createCriteria(Snippet.class)
                    .addOrder(Order.desc("editInfo.creationDate"))
                    .setMaxResults(1)
                    .uniqueResult());
  }

  private MockHttpServletRequestBuilder getViewThumbnail(RecordInformation imageInfo) {
    return get("/gallery/getThumbnail/{id}/{unused}", imageInfo.getId() + "", "12334");
  }

  private MockHttpServletRequestBuilder getViewerImage(RecordInformation imageInfo) {
    return get("/gallery/getViewerImage/{id}/", imageInfo.getId() + "", "12334");
  }

  private BufferedImage getImageFromBytes(byte[] fullImageBytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(fullImageBytes));
  }
}
