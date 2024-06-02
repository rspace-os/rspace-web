package com.researchspace.service;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignatureStatus;
import com.researchspace.model.User;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class DetailedRecordInformationProviderTest extends SpringTransactionalTest {

  private @Autowired DetailedRecordInformationProvider infoProvider;
  UserSessionTracker userTracker;
  private @Autowired RecordSigningManager signingManager;

  @Before
  public void setup() throws Exception {
    super.setUp();
    userTracker = new UserSessionTracker();
  }

  @Test
  public void getDetailedInfoTestSharedInfo() throws Exception {
    TestGroup group = createTestGroup(2);
    TestGroup group2 = createTestGroup(1);
    grpMgr.addUserToGroup(group.u1().getUsername(), group2.getGroup().getId(), RoleInGroup.DEFAULT);
    User u1 = group.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    userTracker.addUser(u1.getUsername(), new MockHttpSession());
    Notebook notebook =
        createNotebookWithNEntries(folderMgr.getRootFolderForUser(u1).getId(), "nb", 1, u1);
    shareNotebookWithGroup(u1, notebook, group.getGroup(), "edit");
    shareNotebookWithGroup(u1, notebook, group2.getGroup(), "read");
    DetailedRecordInformation resp1 =
        infoProvider.getDetailedRecordInformation(notebook.getId(), userTracker, u1, null, null);
    assertNotNull(resp1.getSharedGroupsAndAccess().get(group.getGroup().getDisplayName()));

    DetailedRecordInformation entry1Info =
        infoProvider.getDetailedRecordInformation(
            notebook.getChildrens().iterator().next().getId(), userTracker, u1, null, null);
    assertTrue(entry1Info.getImplicitShares().containsKey(notebook.getGlobalIdentifier()));
    assertTrue(
        entry1Info
            .getImplicitShares()
            .get(notebook.getGlobalIdentifier())
            .contains(group.getGroup().getDisplayName()));
    assertTrue(
        entry1Info
            .getImplicitShares()
            .get(notebook.getGlobalIdentifier())
            .contains(group2.getGroup().getDisplayName()));
  }

  @Test
  public void getDetailedInfoTest() throws Exception {

    User user = createInitAndLoginAnyUser();
    userTracker.addUser(user.getUsername(), new MockHttpSession());

    // check info for normal record
    StructuredDocument record = createBasicDocumentInRootFolderWithText(user, "text1");
    Long recordId = record.getId();
    String initialName = record.getName();
    long initialVersion = record.getUserVersion().getVersion();

    DetailedRecordInformation initialInfo =
        infoProvider.getDetailedRecordInformation(recordId, userTracker, user, null, null);
    assertFalse(initialInfo.getOid().hasVersionId());
    assertEquals(initialName, initialInfo.getName());
    assertEquals(initialVersion, initialInfo.getVersion());
    assertEquals(EditStatus.VIEW_MODE.toString(), initialInfo.getStatus());
    assertEquals(null, initialInfo.getCurrentEditor());
    assertFalse(initialInfo.getSigned());
    assertFalse(initialInfo.getWitnessed());
    assertEquals(SignatureStatus.UNSIGNED, initialInfo.getSignatureStatus());

    // rename the record, start editing the record, check info
    String newName = "newname";
    recordMgr.renameRecord(newName, recordId, user);
    recordMgr.requestRecordEdit(recordId, user, activeUsers);
    DetailedRecordInformation editedInfo =
        infoProvider.getDetailedRecordInformation(recordId, userTracker, user, null, null);
    assertEquals(newName, editedInfo.getName());
    assertTrue(editedInfo.getVersion() > initialVersion);
    assertEquals(EditStatus.EDIT_MODE.toString(), editedInfo.getStatus());
    assertEquals(null, editedInfo.getCurrentEditor());
    assertFalse(editedInfo.getSigned());
    assertFalse(editedInfo.getWitnessed());
    assertEquals(SignatureStatus.UNSIGNED, editedInfo.getSignatureStatus());

    // sign the record, and check the info
    recordMgr.unlockRecord(recordId, user.getUsername());
    signingManager.signRecord(recordId, user, null, "signing");
    DetailedRecordInformation signedInfo =
        infoProvider.getDetailedRecordInformation(recordId, userTracker, user, null, null);

    assertEquals(EditStatus.CAN_NEVER_EDIT.toString(), signedInfo.getStatus());
    assertEquals(null, signedInfo.getCurrentEditor());
    assertTrue(signedInfo.getSigned());
    assertFalse(signedInfo.getWitnessed());
    assertEquals(SignatureStatus.SIGNED_AND_LOCKED, signedInfo.getSignatureStatus());
  }

  @Test
  public void getMediaFileDetailedInfoTest() throws Exception {

    User user = loginAndAddToActiveUsers();
    // check image details
    EcatImage image = addImageToGallery(user);
    Long imageId = image.getId();

    DetailedRecordInformation imageInfo =
        infoProvider.getDetailedRecordInformation(imageId, userTracker, user, null, null);

    assertEquals(imageId, imageInfo.getId());
    assertEquals(image.getWidthResized(), imageInfo.getWidthResized());
    assertEquals(image.getHeightResized(), imageInfo.getHeightResized());
    assertEquals(image.getSize(), imageInfo.getSize());
    assertEquals(image.getVersion(), imageInfo.getVersion());
    assertEquals(EditStatus.VIEW_MODE.toString(), imageInfo.getStatus());

    // check document details
    EcatDocumentFile docFile = addDocumentToGallery(user);
    Long docFileId = docFile.getId();

    DetailedRecordInformation docInfo =
        infoProvider.getDetailedRecordInformation(docFileId, userTracker, user, null, null);

    assertEquals(docFileId, docInfo.getId());
    assertEquals(0, docInfo.getWidthResized());
    assertEquals(0, docInfo.getHeightResized());
    assertEquals(docFile.getSize(), docInfo.getSize());
    assertEquals(docFile.getVersion(), docInfo.getVersion());
    assertEquals(EditStatus.VIEW_MODE.toString(), imageInfo.getStatus());
  }

  private User loginAndAddToActiveUsers() {
    User user = createInitAndLoginAnyUser();
    userTracker.addUser(user.getUsername(), new MockHttpSession());
    return user;
  }

  @Test
  public void getLinkedByInfoTest() throws Exception {
    User user = loginAndAddToActiveUsers();

    // create two docs, link from first to second one
    StructuredDocument userDoc = createBasicDocumentInRootFolderWithText(user, "user doc");
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "target doc");
    addInternalLinkToField(userDoc.getFields().get(0), targetDoc);

    // as user2 create another document, not visible for user, and link it
    User user2 = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user2);
    StructuredDocument user2doc = createBasicDocumentInRootFolderWithText(user2, "user2's doc");
    addInternalLinkToField(user2doc.getFields().get(0), targetDoc);

    logoutAndLoginAs(user);

    // check get info and link information
    DetailedRecordInformation myRecordInfo =
        infoProvider.getDetailedRecordInformation(userDoc.getId(), userTracker, user, null, null);

    assertEquals(0, myRecordInfo.getLinkedByCount());

    List<RecordInformation> linkedBy1 = infoProvider.getLinkedByRecords(userDoc.getId(), user);
    assertEquals(0, linkedBy1.size());

    // check target doc details
    DetailedRecordInformation targetRecordInfo =
        infoProvider.getDetailedRecordInformation(targetDoc.getId(), userTracker, user, null, null);

    assertEquals(2, targetRecordInfo.getLinkedByCount());

    List<RecordInformation> linkedBy2 = infoProvider.getLinkedByRecords(targetDoc.getId(), user);
    assertEquals(2, linkedBy2.size());
    assertEquals(userDoc.getGlobalIdentifier(), linkedBy2.get(0).getOid().toString());
    assertEquals(userDoc.getName(), linkedBy2.get(0).getName());
    assertEquals(userDoc.getOwner().getFullName(), linkedBy2.get(0).getOwnerFullName());

    // user2doc doc is not visible for user, so only owner's details are displayed
    assertEquals(null, linkedBy2.get(1).getOid());
    assertEquals(null, linkedBy2.get(1).getName());
    assertEquals(user2doc.getOwner().getFullName(), linkedBy2.get(1).getOwnerFullName());
  }

  @Test
  public void getGalleryFolderInfoTest() throws Exception {

    User user = loginAndAddToActiveUsers();

    Folder imgGalleryFolder = recordMgr.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, user);
    Folder imgGallerySubfolder = createFolder("my images", imgGalleryFolder, user);

    Long subfolderId = imgGallerySubfolder.getId();
    String expectedGlobalId = "GF" + subfolderId;
    assertEquals(expectedGlobalId, imgGallerySubfolder.getGlobalIdentifier());

    // check folder details
    DetailedRecordInformation subfolderInfo =
        infoProvider.getDetailedRecordInformation(subfolderId, userTracker, user, null, null);
    assertEquals(subfolderId, subfolderInfo.getId());
    assertEquals(expectedGlobalId, subfolderInfo.getOid().toString());
    assertEquals(imgGallerySubfolder.getName(), subfolderInfo.getName());
  }
}
