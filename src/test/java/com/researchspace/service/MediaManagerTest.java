package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class MediaManagerTest extends SpringTransactionalTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {}

  @Test
  public void saveChemElementRequiresPermissionOnParentRecord() throws Exception {
    User newUser = createAndSaveRandomUser();
    final User other = createAndSaveRandomUser();
    logoutAndLoginAs(newUser);
    initialiseContentWithEmptyContent(newUser);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(newUser, "any");

    // now lets create a new Chem element
    final Field field = sd.getFields().iterator().next();
    final String newChemElementMolString = RSpaceTestUtils.getExampleChemString();
    final String imageBase64 = RSpaceTestUtils.getChemImage();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(newChemElementMolString)
            .imageBase64(imageBase64)
            .fieldId(field.getId())
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();
    RSChemElement rsChemElement = rsChemElementManager.saveChemElement(chemicalData, newUser);
    Long rsChemElemId = rsChemElement.getId();
    assertNotNull(rsChemElemId);
    assertEquals(field.getId(), (Long) rsChemElement.getParentId());
    chemicalData.setRsChemElementId(rsChemElemId);
    // now we'll save it; this checks read permission since we need to load an existing chem element
    rsChemElement = rsChemElementManager.saveChemElement(chemicalData, newUser);
    assertNotNull(rsChemElement);

    ChemicalImageDTO chemicalImageDTO = new ChemicalImageDTO(rsChemElemId, imageBase64);
    rsChemElement = rsChemElementManager.saveChemImage(chemicalImageDTO, newUser);
    assertNotNull(rsChemElement);
    assertNotNull(rsChemElement.getImageFileProperty());

    logoutAndLoginAs(other);
    assertAuthorisationExceptionThrown(
        () -> rsChemElementManager.saveChemElement(chemicalData, other));
    assertAuthorisationExceptionThrown(
        () -> rsChemElementManager.saveChemImage(chemicalImageDTO, other));
  }

  @Test
  public void testSaveGetMathElement() throws Exception {
    // happy case save a math element
    User newUser = createAndSaveRandomUser();
    User other = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newUser, other);
    logoutAndLoginAs(newUser);
    RSMath anyMath = TestFactory.createAMathElement();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(newUser, "any");
    RSMath savedMath =
        mediaMgr.saveMath(
            svgAsString(anyMath), sd.getFields().get(0).getId(), anyMath.getLatex(), null, newUser);
    assertNotNull(savedMath.getId());
    assertEquals(anyMath.getMathSvg().getData().length, savedMath.getMathSvg().getData().length);

    // update existing math element
    String newLatex = "\\sqrt(x)";
    RSMath updatedMath =
        mediaMgr.saveMath(
            svgAsString(anyMath),
            sd.getFields().get(0).getId(),
            newLatex,
            savedMath.getId(),
            newUser);
    assertEquals(newLatex, updatedMath.getLatex());
    assertEquals("The ids should be the same", savedMath.getId(), updatedMath.getId());

    logoutAndLoginAs(other);
    assertAuthorisationExceptionThrown(
        () ->
            mediaMgr.saveMath(
                svgAsString(anyMath),
                sd.getFields().get(0).getId(),
                newLatex,
                savedMath.getId(),
                other));
  }

  private String svgAsString(RSMath anyMath) {
    return new String(anyMath.getMathSvg().getData(), StandardCharsets.UTF_8);
  }

  @Test
  public void gettingImage() throws IOException {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    EcatImage img = addImageToGallery(user);
    clearSessionAndEvictAll();

    assertNotNull(img.getWorkingImageFP());
    assertNotNull(img.getThumbnailImageFP());
    assertNull(img.getWorkingImage());

    assertNull(img.getImageThumbnailed());
  }

  @Test
  public void saveImageCheckOrientationMaintained() throws IOException {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    EcatImage img = addImageToGallery(user, "testimages/imageWithOrientationData.JPG");
    clearSessionAndEvictAll();
    assertNotNull(img.getWorkingImageFP());
    assertNotNull(img.getThumbnailImageFP());
    assertNull(img.getWorkingImage());
    assertNull(img.getImageThumbnailed());

    BufferedImage original =
        Thumbnails.of(fileStore.retrieve(img.getFileProperty()).get()).scale(1).asBufferedImage();
    BufferedImage scaled =
        Thumbnails.of(fileStore.retrieve(img.getWorkingImageFP()).get()).scale(1).asBufferedImage();

    double originalAspectRatio = (double) original.getWidth() / (double) original.getHeight();
    double workingImgAspectRatio = (double) scaled.getWidth() / (double) scaled.getHeight();

    assertEquals(originalAspectRatio, workingImgAspectRatio, 0.1);
  }

  @Test
  public void insertOrAddCommentRequiresPermissionOnParentRecord() throws Exception {
    User anyUser = createAndSaveRandomUser();
    final User otherUser = createAndSaveRandomUser();
    logoutAndLoginAs(anyUser);
    initialiseContentWithEmptyContent(anyUser);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(anyUser, "any");

    // now lets create a new comment
    final long fieldId = sd.getFields().iterator().next().getId();
    final EcatComment comm = mediaMgr.insertEcatComment(fieldId + "", "A comment", anyUser);
    assertNotNull(comm);
    mediaMgr.addEcatComment(fieldId + "", comm.getComId() + "", "comm1", anyUser);

    logoutAndLoginAs(otherUser);
    assertAuthorisationExceptionThrown(
        () -> mediaMgr.insertEcatComment(fieldId + "", "A comment", otherUser));
    assertAuthorisationExceptionThrown(
        () -> mediaMgr.addEcatComment(fieldId + "", comm.getComId() + "", "A comment", otherUser));
  }

  @Test
  public void savingNewVersionOfEcatImage() throws IOException {

    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    InputStream picture1InputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    EcatImage image = mediaMgr.saveNewImage("Picture1.png", picture1InputStream, user, null);
    assertNotNull(image.getId());
    assertEquals(1, image.getVersion());

    EcatImage updatedImage = updateImageInGallery(image.getId(), user);
    assertNotNull(updatedImage.getId());
    assertEquals(2, updatedImage.getVersion());

    // check that media file details are updated
    assertEquals(image.getId(), updatedImage.getId());
    assertEquals(image.getCreationDate(), updatedImage.getCreationDate());
    assertNotEquals(image.getName(), updatedImage.getName());
    assertNotEquals(image.getModificationDate(), updatedImage.getModificationDate());
    assertNotEquals(image.getSize(), updatedImage.getSize());

    // check that image details are updated
    assertNotEquals(image.getFileName(), updatedImage.getFileName());
    assertNotEquals(image.getWorkingImageFP().getId(), updatedImage.getWorkingImageFP().getId());
    assertNotEquals(
        image.getThumbnailImageFP().getId(), updatedImage.getThumbnailImageFP().getId());
  }

  @Test
  public void checkFilePropertyFilenameTruncationWhenSavingUnknownDoc() throws Exception {

    /* when saving a file with very long name the generated file property filename
     * may require truncation so it fits db column */

    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    // very long name scenario
    String longNameShortExtension =
        StringUtils.repeat("a", BaseRecord.DEFAULT_VARCHAR_LENGTH - 5) + ".docx";
    EcatDocumentFile doc =
        mediaMgr.saveNewDocument(
            longNameShortExtension,
            IOUtils.toInputStream("testInputStream", "UTF-8"),
            user,
            null,
            null);
    assertNotNull(doc);
    assertEquals(longNameShortExtension, doc.getName());
    String savedFPFilename = doc.getFileProperty().getFileName();
    assertEquals(BaseRecord.DEFAULT_VARCHAR_LENGTH, savedFPFilename.length());
    String expectedFPFilenameRegex =
        "a{" + (BaseRecord.DEFAULT_VARCHAR_LENGTH - 22) + "}\\.\\.\\._(\\d){13}\\.docx";
    assertTrue(
        "unexpected filename: " + savedFPFilename,
        savedFPFilename.matches(expectedFPFilenameRegex));

    // very long extension (filename part after last .) scenario
    String shorNameLongExtension =
        "test." + StringUtils.repeat("a", BaseRecord.DEFAULT_VARCHAR_LENGTH - 5);
    EcatDocumentFile doc2 =
        mediaMgr.saveNewDocument(
            shorNameLongExtension,
            IOUtils.toInputStream("testInputStream2", "UTF-8"),
            user,
            null,
            null);
    assertNotNull(doc2);
    assertEquals(shorNameLongExtension, doc2.getName());
    String savedFPFilename2 = doc2.getFileProperty().getFileName();
    assertEquals(BaseRecord.DEFAULT_VARCHAR_LENGTH, savedFPFilename2.length());
    String expectedFPFilename2Regex =
        "test_(\\d){13}\\.a{" + (BaseRecord.DEFAULT_VARCHAR_LENGTH - 19) + "}";
    assertTrue(
        "unexpected filename: " + savedFPFilename,
        savedFPFilename2.matches(expectedFPFilename2Regex));
  }

  @Test
  public void uploadNewVersionEnforceSameExtension() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    InputStream docInputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("MSattachment.doc");
    EcatDocumentFile doc =
        mediaMgr.saveNewDocument("MSattachment.doc", docInputStream, user, null, null);
    assertNotNull(doc.getId());
    assertEquals(1, doc.getVersion());

    InputStream csvInputStream = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("csv.csv");
    assertExceptionThrown(
        () -> mediaMgr.updateMediaFile(doc.getId(), csvInputStream, "csv.csv", user, null),
        IllegalArgumentException.class);

    // still, uploading legacy ms office formats (e.g. doc->docx) should be allowed
    InputStream docxInputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("PowerPasteTesting_RSpace.docx");
    EcatMediaFile updatedDoc =
        mediaMgr.updateMediaFile(
            doc.getId(), docxInputStream, "MSattachment_converted.docx", user, null);
    assertEquals(doc.getId(), updatedDoc.getId());
    assertEquals(2, updatedDoc.getVersion());

    // extension check should be case-insensitive
    EcatMediaFile updatedAgainDoc =
        mediaMgr.updateMediaFile(doc.getId(), docxInputStream, "NEW_NAME.DOCX", user, null);
    assertEquals(doc.getId(), updatedAgainDoc.getId());
    assertEquals(3, updatedAgainDoc.getVersion());
  }

  @Test
  public void uploadNewVersionChecksMsOfficeLock() throws Exception {

    MediaFileLockHandler lockHandler = mediaMgr.getLockHandler();
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);

    InputStream pictureIS = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    EcatImage image = mediaMgr.saveNewImage("Picture1.png", pictureIS, user, null);
    assertNotNull(image.getId());
    assertEquals(1, image.getVersion());

    // lock the file for MS Office operations
    String lockId = "testLock";
    lockHandler.lock(image.getGlobalIdentifier(), lockId);

    // doesn't work if file is locked and no lock is provided
    assertExceptionThrown(
        () -> mediaMgr.updateMediaFile(image.getId(), pictureIS, "Picture1.png", user, null),
        IllegalStateException.class);

    // works if correct lock is provided
    InputStream pictureIS2 =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    EcatMediaFile image2 =
        mediaMgr.updateMediaFile(image.getId(), pictureIS2, "Picture1.png", user, lockId);
    assertEquals(2, image2.getVersion());

    // works again after unlocking
    lockHandler.unlock(image.getGlobalIdentifier(), lockId);
    InputStream pictureIS3 =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    EcatMediaFile image3 =
        mediaMgr.updateMediaFile(image.getId(), pictureIS3, "Picture1.png", user, null);
    assertEquals(3, image3.getVersion());
  }

  @Test
  public void uploadNewVersionOfAttachmentInSharedDocument() throws Exception {

    User owner = createAndSaveAPi();
    User otherUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(owner, otherUser);

    Group group = createGroup("mediaGroup", owner);
    addUsersToGroup(owner, group, otherUser);

    // create document with attachment
    logoutAndLoginAs(owner);
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(owner, "any");
    Field field = basicDoc.getFields().iterator().next();

    File txtFile = RSpaceTestUtils.getAnyAttachment();
    EcatDocumentFile docFile = addAttachmentDocumentToField(txtFile, field, owner);
    assertNotNull(docFile);
    assertEquals(1, docFile.getVersion());

    // confirm other user has no permissions to attachment
    logoutAndLoginAs(otherUser);
    assertFalse(
        permissionUtils.isPermittedViaMediaLinksToRecords(docFile, PermissionType.READ, otherUser));
    assertFalse(
        permissionUtils.isPermittedViaMediaLinksToRecords(
            docFile, PermissionType.WRITE, otherUser));

    // owner shares doc with read permission
    logoutAndLoginAs(owner);
    shareRecordWithGroup(owner, group, basicDoc);
    List<RecordGroupSharing> sharingInfos = sharingMgr.getRecordSharingInfo(basicDoc.getId());
    assertEquals(1, sharingInfos.size());
    RecordGroupSharing basicDocSharingInfo = sharingInfos.get(0);

    // owner can upload new version of attachment
    EcatMediaFile updatedDocFile =
        mediaMgr.updateMediaFile(
            docFile.getId(), new FileInputStream(txtFile), txtFile.getName(), owner, null);
    assertNotNull(updatedDocFile);
    assertEquals(2, updatedDocFile.getVersion());

    // otherUser should get read permission to attachment
    logoutAndLoginAs(otherUser);
    assertTrue(
        permissionUtils.isPermittedViaMediaLinksToRecords(docFile, PermissionType.READ, otherUser));
    assertFalse(
        permissionUtils.isPermittedViaMediaLinksToRecords(
            docFile, PermissionType.WRITE, otherUser));

    // otherUser can't upload new version, as have only read permission
    assertAuthorisationExceptionThrown(
        () ->
            mediaMgr.updateMediaFile(
                docFile.getId(), new FileInputStream(txtFile), txtFile.getName(), otherUser, null));

    // owner shares with edit permission
    logoutAndLoginAs(owner);
    sharingMgr.updatePermissionForRecord(basicDocSharingInfo.getId(), "write", owner.getUsername());

    // otherUser should get read permission to attachment
    logoutAndLoginAs(otherUser);
    assertTrue(
        permissionUtils.isPermittedViaMediaLinksToRecords(docFile, PermissionType.READ, otherUser));
    assertTrue(
        permissionUtils.isPermittedViaMediaLinksToRecords(
            docFile, PermissionType.WRITE, otherUser));

    // otherUser can upload new version of attachment shared for edit
    EcatMediaFile docFileUpdatedByOtherUser =
        mediaMgr.updateMediaFile(
            docFile.getId(), new FileInputStream(txtFile), txtFile.getName(), otherUser, null);
    assertNotNull(docFileUpdatedByOtherUser);
    assertEquals(3, docFileUpdatedByOtherUser.getVersion());
  }
}
