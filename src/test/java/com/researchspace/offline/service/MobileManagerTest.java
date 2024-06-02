package com.researchspace.offline.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.offline.model.OfflineImage;
import com.researchspace.offline.model.OfflineRecord;
import com.researchspace.offline.model.OfflineRecordInfo;
import com.researchspace.offline.model.OfflineWorkType;
import com.researchspace.offline.service.impl.MobileManagerImpl;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class MobileManagerTest extends SpringTransactionalTest {

  private @Autowired MobileManager mobileManager;
  private @Autowired RecordDeletionManager recordDeletionManager;
  private @Autowired OfflineManager offlineManager;
  protected @Autowired MediaManager mediaMgr;
  protected @Autowired RSChemElementManager chemMgr;
  protected @Autowired RichTextUpdater richTextUpdater;

  private User offlineUser1;
  private User offlineUser2;

  private StructuredDocument doc1;
  private StructuredDocument doc2;

  private UserSessionTracker activeUsers;

  private boolean offlineUserInitialised;

  @Before
  public void setUp() {
    initOfflineUserWithTwoDocs();
  }

  // creates user with two docs marked for offline work
  public void initOfflineUserWithTwoDocs() {
    if (offlineUserInitialised) {
      return;
    }

    offlineUser1 = createAndSaveUserIfNotExists("offlineUser1", Constants.PI_ROLE);
    offlineUser2 = createAndSaveUserIfNotExists("offlineUser2", Constants.PI_ROLE);

    initialiseContentWithEmptyContent(offlineUser1, offlineUser2);

    doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    doc2 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc2");

    activeUsers = anySessionTracker();
    activeUsers.addUser("offlineUser1", new MockHttpSession());

    offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
    offlineManager.addRecordForOfflineWork(doc2, offlineUser1, activeUsers);

    offlineUserInitialised = true;
  }

  private StructuredDocument getBasicOfflineDocumentWithImageChemAndAnnotation(User user)
      throws Exception {
    StructuredDocument doc =
        createBasicDocumentInRootFolderWithText(user, "testDoc with img and chem");
    Field field = doc.getFields().get(0);

    addImageToField(field, user);
    addChemStructureToField(field, user);
    addImageAnnotationToField(field, user);

    return doc;
  }

  @Test
  public void testSimpleGetRecord() throws Exception {

    OfflineRecord retrievedNewRecord = mobileManager.getRecord(doc1.getId(), "offlineUser1");
    String retrievedNewContent = retrievedNewRecord.getContent();

    assertNotNull(retrievedNewRecord);
    assertEquals(doc1.getName(), retrievedNewRecord.getName());
    assertEquals("testDoc1", retrievedNewContent);
  }

  @Test
  public void testGetRecordWithImageChemAndAnnotation() throws Exception {

    StructuredDocument doc = getBasicOfflineDocumentWithImageChemAndAnnotation(offlineUser1);
    offlineManager.addRecordForOfflineWork(doc, offlineUser1, activeUsers);

    OfflineRecord record = mobileManager.getRecord(doc.getId(), "offlineUser1");
    assertNotNull(record);
    assertNotNull(record.getImages());
    assertEquals(4, record.getImages().size());
  }

  @Test
  public void testSimpleGetListAndRecords() throws Exception {

    List<OfflineRecordInfo> mobileWorkList = mobileManager.getOfflineRecordList("offlineUser1");
    assertEquals(2, mobileWorkList.size());

    Long recordId = mobileWorkList.get(0).getId();
    OfflineWorkType recordLockType = mobileWorkList.get(0).getLockType();

    OfflineRecord record = mobileManager.getRecord(recordId, "offlineUser1");
    assertEquals(recordId, record.getId());
    assertEquals(recordLockType, record.getLockType());
    assertEquals("/", record.getPath());
  }

  @Test(expected = AuthorizationException.class)
  public void testGetRecordChecksPermissions() throws Exception {

    // permissions check is using SecurityUtils.getSubject(). at this point it is user2 who is
    // logged
    Record doc = createBasicDocumentInRootFolderWithText(offlineUser1, "testDocForOfflineUser1");
    mobileManager.getRecord(doc.getId(), "offlineUser2");
  }

  @Test
  public void getOfflineRecordListOmitsDeletedRecord()
      throws IllegalAddChildOperation, DocumentAlreadyEditedException {

    List<OfflineRecordInfo> mobileWorkList = mobileManager.getOfflineRecordList("offlineUser1");
    assertEquals(2, mobileWorkList.size());

    recordDeletionManager.deleteRecord(doc2.getParent().getId(), doc2.getId(), offlineUser1);

    List<OfflineRecordInfo> mobileWorkList2 = mobileManager.getOfflineRecordList("offlineUser1");
    assertEquals(1, mobileWorkList2.size());
  }

  @Test
  public void uploadNewRecordWithTextAndSketch() throws Exception {

    String testName = "newRecordName";
    String testSketchAnnotation = "zwibbler.testAnnotation";
    String testContent = "newRecordTestContent";

    String testImageLink =
        "<img data-type=\"sketch\" data-localid=\"8\" width=\"10.5\" height=\"20\" />";
    String savedImageLinkStart = "<img data-type=\"sketch\" data-id=\"";
    String savedImageLinkEnd = "\" width=\"11\" height=\"20\">";

    String testBase64 = "AADDEEDDASDGFDFGF";
    byte[] decodedBytes = Base64.decodeBase64(testBase64);

    OfflineRecord record = new OfflineRecord();
    record.setName(testName);
    record.setContent(testContent + testImageLink);

    OfflineImage sketch = new OfflineImage();
    sketch.setClientId(8L);
    sketch.setType("sketch");
    sketch.setAnnotation(testSketchAnnotation);
    sketch.setData(decodedBytes);
    record.addImage(sketch);

    Long newRecordId = mobileManager.uploadRecord(record, "offlineUser1");
    assertNotNull(newRecordId);

    OfflineRecord retrievedRecord = mobileManager.getRecord(newRecordId, "offlineUser1");
    String retrievedContent = retrievedRecord.getContent();

    assertNotNull(retrievedRecord);
    assertEquals(testName, retrievedRecord.getName());
    assertTrue("wrong content: " + retrievedContent, retrievedContent.startsWith(testContent));
    assertTrue(
        "wrong content: " + retrievedContent, retrievedContent.contains(savedImageLinkStart));
    assertTrue("wrong content: " + retrievedContent, retrievedContent.endsWith(savedImageLinkEnd));
    assertEquals(OfflineWorkType.EDIT, retrievedRecord.getLockType());

    List<OfflineImage> images = retrievedRecord.getImages();
    assertNotNull(images);
    assertEquals(1, images.size());

    assertTrue("saved image id == " + images.get(0).getId(), images.get(0).getId() > 0);
    assertEquals(testSketchAnnotation, images.get(0).getAnnotation());
    assertEquals(new String(decodedBytes), new String(images.get(0).getData()));
  }

  @Test
  public void uploadNewRecordWithTwoSketches() throws Exception {

    String testName = "newRecordTwoSketches";

    String testImageLink1 =
        "<img data-type=\"sketch\" data-localid=\"8\" width=\"10.5\" height=\"20\" />";
    String testImageLink2 =
        "<img data-type=\"sketch\" data-localid=\"9\" width=\"10\" height=\"20\" />";

    String savedImageLinksStart = "<img data-type=\"sketch\" data-id=\"";
    String savedImageLinksMiddle =
        "\" width=\"11\" height=\"20\"><img data-type=\"sketch\" data-id=\"";
    String savedImageLinkEnd = "\" width=\"10\" height=\"20\">";

    OfflineRecord record = new OfflineRecord();
    record.setName(testName);
    record.setContent(testImageLink1 + testImageLink2);

    OfflineImage sketch1 = new OfflineImage();
    sketch1.setClientId(8L);
    sketch1.setType("sketch");
    sketch1.setData("testData".getBytes());
    record.addImage(sketch1);

    OfflineImage sketch2 = new OfflineImage();
    sketch2.setClientId(9L);
    sketch2.setType("sketch");
    sketch2.setData("testData2".getBytes());
    record.addImage(sketch2);

    Long newRecordId = mobileManager.uploadRecord(record, "offlineUser1");
    assertNotNull(newRecordId);

    OfflineRecord retrievedRecord = mobileManager.getRecord(newRecordId, "offlineUser1");
    String retrievedContent = retrievedRecord.getContent();

    assertNotNull(retrievedRecord);
    assertEquals(testName, retrievedRecord.getName());
    assertTrue(
        "wrong content: " + retrievedContent, retrievedContent.startsWith(savedImageLinksStart));
    assertTrue(
        "wrong content: " + retrievedContent, retrievedContent.contains(savedImageLinksMiddle));
    assertTrue("wrong content: " + retrievedContent, retrievedContent.endsWith(savedImageLinkEnd));
    assertEquals(OfflineWorkType.EDIT, retrievedRecord.getLockType());

    List<OfflineImage> images = retrievedRecord.getImages();
    assertNotNull(images);
    assertEquals(2, images.size());

    assertTrue("first id == " + images.get(0).getId(), images.get(0).getId() > 0);
    assertTrue("second id == " + images.get(1).getId(), images.get(1).getId() > 0);
  }

  @Test
  public void uploadSimpleModifiedContentRecord() throws Exception {

    StructuredDocument doc3 =
        createBasicDocumentInRootFolderWithText(offlineUser1, "newTestContent");
    offlineManager.addRecordForOfflineWork(doc3, offlineUser1, activeUsers);

    StructuredDocument newDoc3 = recordMgr.get(doc3.getId()).asStrucDoc();
    Long originalDocVersion = newDoc3.getUserVersion().getVersion();

    OfflineRecord retrievedNewRecord = mobileManager.getRecord(doc3.getId(), "offlineUser1");

    String updatedContent = "updatedTestContent";
    retrievedNewRecord.setContent(updatedContent);

    Long uploadedRecordId = mobileManager.uploadRecord(retrievedNewRecord, "offlineUser1");
    assertEquals(doc3.getId(), uploadedRecordId);

    OfflineRecord retrievedModifiedRecord =
        mobileManager.getRecord(uploadedRecordId, "offlineUser1");
    String retrievedModifiedContent = retrievedModifiedRecord.getContent();

    assertNotNull(retrievedModifiedRecord);
    assertEquals(updatedContent, retrievedModifiedContent);

    StructuredDocument updatedDoc3 = (StructuredDocument) recordMgr.get(uploadedRecordId);
    Long expectedDocVersion = originalDocVersion + 1;
    assertEquals(expectedDocVersion, updatedDoc3.getUserVersion().getVersion());
  }

  @Test
  public void createsNewRecordFromModifiedRecordIfServerCopyWasModified() throws Exception {

    StructuredDocument doc3 =
        createBasicDocumentInRootFolderWithText(offlineUser1, "newTestContent");
    offlineManager.addRecordForOfflineWork(doc3, offlineUser1, activeUsers);

    // retrieve record for mobile
    OfflineRecord retrievedNewRecord = mobileManager.getRecord(doc3.getId(), "offlineUser1");

    // update record on a server
    doc3.getFields().get(0).setFieldData("modifiedTestContent");
    recordMgr.save(doc3, offlineUser1);

    // upload modified record from mobile
    String updatedName = "updatedTestName";
    String updatedContent = "updatedTestContent";
    retrievedNewRecord.setName(updatedName);
    retrievedNewRecord.setContent(updatedContent);
    Long uploadedRecordId = mobileManager.uploadRecord(retrievedNewRecord, "offlineUser1");

    // should create new record
    assertNotEquals(doc3.getId(), uploadedRecordId);

    OfflineRecord retrievedModifiedRecord =
        mobileManager.getRecord(uploadedRecordId, "offlineUser1");
    String retrievedModifiedName = retrievedModifiedRecord.getName();
    String retrievedModifiedContent = retrievedModifiedRecord.getContent();

    assertNotNull(retrievedModifiedRecord);
    assertEquals(updatedName + MobileManagerImpl.CONFLICTING_RECORD_SUFFIX, retrievedModifiedName);
    assertEquals(updatedContent, retrievedModifiedContent);
  }

  @Test
  public void conflictingRecordHasCopiedImagesAndSameLocationAsOriginal() throws Exception {

    String subfolderName = "offlineTestSubFolder";
    StructuredDocument doc3 =
        createBasicDocumentInRootSubfolderWithText(offlineUser1, subfolderName, "newTestContent");
    offlineManager.addRecordForOfflineWork(doc3, offlineUser1, activeUsers);

    Field field = doc3.getFields().get(0);
    addImageAnnotationToField(field, offlineUser1);

    // retrieve record for mobile
    OfflineRecord retrievedNewRecord = mobileManager.getRecord(doc3.getId(), "offlineUser1");
    assertEquals("/" + subfolderName + "/", retrievedNewRecord.getPath());

    // update record on a server
    field.setFieldData(field.getFieldData() + "modifiedTestContent");
    recordMgr.save(doc3, offlineUser1);

    // upload modified record from mobile
    String updatedName = "updatedTestName";
    retrievedNewRecord.setName(updatedName);
    Long uploadedRecordId = mobileManager.uploadRecord(retrievedNewRecord, "offlineUser1");

    // should create new record
    assertNotEquals(doc3.getId(), uploadedRecordId);

    OfflineRecord retrievedModifiedRecord =
        mobileManager.getRecord(uploadedRecordId, "offlineUser1");
    String retrievedModifiedName = retrievedModifiedRecord.getName();

    assertNotNull(retrievedModifiedRecord);
    assertEquals(updatedName + MobileManagerImpl.CONFLICTING_RECORD_SUFFIX, retrievedModifiedName);
    // path should stay the same
    assertEquals(retrievedNewRecord.getPath(), retrievedModifiedRecord.getPath());

    // record should still have the annotation attached to it
    List<OfflineImage> retrievedImages = retrievedModifiedRecord.getImages();
    assertNotNull(retrievedImages);
    assertEquals(2, retrievedImages.size());
  }

  @Test
  public void uploadUnmodifiedRecordWithImageChemAndAnnotation() throws Exception {

    StructuredDocument doc = getBasicOfflineDocumentWithImageChemAndAnnotation(offlineUser1);
    offlineManager.addRecordForOfflineWork(doc, offlineUser1, activeUsers);

    OfflineRecord record = mobileManager.getRecord(doc.getId(), "offlineUser1");
    assertNotNull(record);
    assertEquals(4, record.getImages().size());

    // upload unmodified record
    Long unmodifiedRecordId = mobileManager.uploadRecord(record, "offlineUser1");
    assertEquals(doc.getId(), unmodifiedRecordId);

    // retrieve uploaded record. the only difference should be modification date
    OfflineRecord retrievedUnmodifiedRecord =
        mobileManager.getRecord(unmodifiedRecordId, "offlineUser1");
    assertNotEquals(
        record.getLastSynchronisedModificationTime(),
        retrievedUnmodifiedRecord.getLastSynchronisedModificationTime());
    assertEquals(record.getName(), retrievedUnmodifiedRecord.getName());
    assertEquals(record.getContent(), retrievedUnmodifiedRecord.getContent());
    assertEquals(record.getImages().size(), retrievedUnmodifiedRecord.getImages().size());
  }

  @Test
  public void uploadRecordWithModifiedAnnotation() throws Exception {

    StructuredDocument doc =
        createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc with annotation");
    offlineManager.addRecordForOfflineWork(doc, offlineUser1, activeUsers);

    Field field = doc.getFields().get(0);
    addImageAnnotationToField(field, offlineUser1);

    OfflineRecord record = mobileManager.getRecord(doc.getId(), "offlineUser1");
    assertNotNull(record);
    assertEquals(2, record.getImages().size());

    OfflineImage annotation = record.getImages().get(1);
    annotation.setAnnotation(getTestZwibblerAnnotationString("modifiedAnnotation"));

    // upload record with modified annotation
    Long modifiedRecordId = mobileManager.uploadRecord(record, "offlineUser1");
    assertEquals(doc.getId(), modifiedRecordId);

    OfflineRecord retrievedModifiedRecord =
        mobileManager.getRecord(modifiedRecordId, "offlineUser1");
    assertNotEquals(
        record.getLastSynchronisedModificationTime(),
        retrievedModifiedRecord.getLastSynchronisedModificationTime());
    assertEquals(
        annotation.getAnnotation(), retrievedModifiedRecord.getImages().get(1).getAnnotation());
  }
}
