package com.researchspace.offline.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.OfflineWorkStatus;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.offline.dao.OfflineRecordUserDao;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.offline.model.OfflineWorkType;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

@Ignore
public class OfflineManagerTest extends SpringTransactionalTest {

  @Autowired private OfflineManager offlineManager;

  @Autowired private OfflineRecordUserDao offlineRecordUserDao;

  @Autowired private RecordSharingManager recordSharingMgr;

  @Autowired private RecordManager recordManager;

  private User offlineUser1;
  private User offlineUser2;
  private Group offlineGroup;
  private UserSessionTracker activeUsers;

  private boolean offlineUserInitialised;

  @Before
  public void setUp() {
    initOfflineUsers();
  }

  // creates two users and a group
  private void initOfflineUsers() {
    if (offlineUserInitialised) {
      return;
    }

    offlineUser1 = createAndSaveUserIfNotExists("offlineUser1", Constants.PI_ROLE);
    offlineUser2 = createAndSaveUserIfNotExists("offlineUser2");
    activeUsers = anySessionTracker();
    activeUsers.addUser("offlineUser1", new MockHttpSession());
    activeUsers.addUser("offlineUser2", new MockHttpSession());

    initialiseContentWithEmptyContent(offlineUser1);
    initialiseContentWithEmptyContent(offlineUser2);

    offlineGroup = createGroup("offlineGroup", offlineUser1);
    addUsersToGroup(offlineUser1, offlineGroup, offlineUser2);

    offlineUserInitialised = true;
  }

  @Test
  public void testAddRecordForOfflineWork() {

    Record doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    OfflineRecordUser createdWork =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);

    assertNotNull("id shouldn't be null", createdWork.getId());
    assertNotNull("creation date shouldn't be null", createdWork.getCreationDate());
    assertNotNull("type shouldn't be null", createdWork.getWorkType());
  }

  @Test
  public void offlineEditOrViewDependingOnPermissions() {
    logoutAndLoginAs(offlineUser1);
    // user can edit own record
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    OfflineRecordUser createdWork1 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
    assertEquals(OfflineWorkType.EDIT, createdWork1.getWorkType());

    // record shared with 'read' permission can only be viewed by other user (but owner can still
    // edit)
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc2");
    shareRecordWithOfflineGroup(doc2, "read");
    logoutAndLoginAs(offlineUser2);
    OfflineRecordUser createdWork2 =
        offlineManager.addRecordForOfflineWork(doc2, offlineUser2, activeUsers);
    logoutAndLoginAs(offlineUser1);
    OfflineRecordUser createdWork3 =
        offlineManager.addRecordForOfflineWork(doc2, offlineUser1, activeUsers);

    assertEquals(OfflineWorkType.VIEW, createdWork2.getWorkType());
    assertEquals(OfflineWorkType.EDIT, createdWork3.getWorkType());
  }

  @Test
  public void firstUserCanOfflineEditSecondCanView() {

    // create document shared for edit in group
    logoutAndLoginAs(offlineUser1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    shareRecordWithOfflineGroup(doc1, "edit");

    // check with owner locking first
    OfflineRecordUser createdWork1 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
    OfflineRecordUser createdWork2 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser2, activeUsers);
    assertEquals(OfflineWorkType.EDIT, createdWork1.getWorkType());
    assertEquals(OfflineWorkType.VIEW, createdWork2.getWorkType());

    // release lock for offline users 1 and 2
    offlineManager.removeRecordFromOfflineWork(doc1.getId(), offlineUser1);
    offlineManager.removeRecordFromOfflineWork(doc1.getId(), offlineUser2);
    flushDatabaseState();

    // check with other group member locking first
    OfflineRecordUser createdWork3 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser2, activeUsers);
    OfflineRecordUser createdWork4 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);

    assertEquals(OfflineWorkType.EDIT, createdWork3.getWorkType());
    assertEquals(OfflineWorkType.VIEW, createdWork4.getWorkType());
  }

  @Test(expected = AuthorizationException.class)
  public void exceptionIfNoPermissionToRecordSelectedForOfflineWork() {
    // first user creates document but doesn't share it
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    // second user tries mark the document for offline - should return exception
    offlineManager.addRecordForOfflineWork(doc1, offlineUser2, activeUsers);
  }

  @Test(expected = IllegalStateException.class)
  public void exceptionIfRecordIsCurrentlyEdited() {

    logoutAndLoginAs(offlineUser1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    shareRecordWithOfflineGroup(doc1, "edit");
    logoutAndLoginAs(offlineUser2);
    // second user opens document for edit
    EditStatus editStatus =
        recordManager.requestRecordEdit(doc1.getId(), offlineUser2, activeUsers);
    assertEquals(EditStatus.EDIT_MODE, editStatus);

    // first user tries marking offline - should return exception
    offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
  }

  @SuppressWarnings("unused")
  @Test
  public void lockingRecordForSecondTimeEndsWithSecondLock() {

    Record doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    OfflineRecordUser createdWork1 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
    OfflineRecordUser createdWork2 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);

    // read current usage
    List<OfflineRecordUser> offlineUsage = offlineRecordUserDao.getOfflineWorkForRecord(doc1);
    assertEquals(1, offlineUsage.size());
    assertEquals(createdWork2.getId(), offlineUsage.get(0).getId());
  }

  @Test
  public void onlyStructureDocumentsAreApplicableForOffline() throws InterruptedException {
    // create a basic document, folder, notebook
    StructuredDocument basicDocument =
        createBasicDocumentInRootSubfolderWithText(offlineUser1, "testDir", "test");
    BaseRecord folder = basicDocument.getParent();
    BaseRecord notebook =
        createNotebookWithNEntries(folder.getId(), "testOfflineNotebook", 1, offlineUser1);

    RSForm notBasicDocumentForm = new RSForm("myTestOfflineForm", "my form", offlineUser1);
    StructuredDocument notBasicDocument =
        createBasicDocumentInRootFolderWithText(offlineUser1, "test2");
    notBasicDocument.setForm(notBasicDocumentForm);

    assertTrue("basic document seems not to be basic document", basicDocument.isBasicDocument());
    assertFalse(
        "not real basic document seems to be basic document", notBasicDocument.isBasicDocument());

    List<BaseRecord> docs = new ArrayList<BaseRecord>();
    docs.add(basicDocument);
    docs.add(folder);
    docs.add(notebook);
    docs.add(notBasicDocument);

    // only basic document have applicable offline status
    offlineManager.loadOfflineWorkStatusOfRecords(docs, offlineUser1);
    assertEquals(OfflineWorkStatus.NOT_OFFLINE, basicDocument.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.NOT_APPLICABLE, folder.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.NOT_APPLICABLE, notebook.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.NOT_APPLICABLE, notBasicDocument.getOfflineWorkStatus());

    // only basic document can be marked for offline
    OfflineRecordUser result =
        offlineManager.addRecordForOfflineWork(basicDocument, offlineUser1, activeUsers);
    assertEquals(OfflineWorkType.EDIT, result.getWorkType());

    boolean exceptionThrown = false;
    try {
      offlineManager.addRecordForOfflineWork(folder, offlineUser1, activeUsers);
    } catch (UnsupportedOperationException uoe) {
      exceptionThrown = true;
    }
    assertTrue("exception not thrown when adding folder for offline", exceptionThrown);

    boolean exceptionThrown2 = false;
    try {
      offlineManager.addRecordForOfflineWork(notebook, offlineUser1, activeUsers);
    } catch (UnsupportedOperationException uoe) {
      exceptionThrown2 = true;
    }
    assertTrue("exception not thrown when adding notebook for offline", exceptionThrown2);

    boolean exceptionThrown3 = false;
    try {
      offlineManager.addRecordForOfflineWork(notBasicDocument, offlineUser1, activeUsers);
    } catch (UnsupportedOperationException uoe) {
      exceptionThrown3 = true;
    }
    assertTrue("exception not thrown when adding not basic document for offline", exceptionThrown3);
  }

  @SuppressWarnings("unused")
  @Test
  public void loadOfflineWorkStatusGeneralTest() {
    logoutAndLoginAs(offlineUser1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc1");
    shareRecordWithOfflineGroup(doc1, "edit");
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc2");
    shareRecordWithOfflineGroup(doc2, "edit");
    StructuredDocument doc3 = createBasicDocumentInRootFolderWithText(offlineUser1, "testDoc3");
    shareRecordWithOfflineGroup(doc3, "view");

    List<BaseRecord> docs = new ArrayList<BaseRecord>();
    docs.add(doc1);
    docs.add(doc2);
    docs.add(doc3);

    offlineManager.loadOfflineWorkStatusOfRecords(docs, offlineUser1);
    assertEquals(OfflineWorkStatus.NOT_OFFLINE, doc1.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.NOT_OFFLINE, doc2.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.NOT_OFFLINE, doc3.getOfflineWorkStatus());

    assertFalse(doc1.isSelectedForOfflineWork());
    assertFalse(doc2.isSelectedForOfflineWork());
    assertFalse(doc3.isSelectedForOfflineWork());

    OfflineRecordUser createdWork1 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser1, activeUsers);
    logoutAndLoginAs(offlineUser2);
    OfflineRecordUser createdWork2 =
        offlineManager.addRecordForOfflineWork(doc1, offlineUser2, activeUsers);
    OfflineRecordUser createdWork3 =
        offlineManager.addRecordForOfflineWork(doc2, offlineUser2, activeUsers);
    OfflineRecordUser createdWork4 =
        offlineManager.addRecordForOfflineWork(doc3, offlineUser2, activeUsers);

    // from offlineUser1 perspective
    logoutAndLoginAs(offlineUser1);
    offlineManager.loadOfflineWorkStatusOfRecords(docs, offlineUser1);
    assertEquals(OfflineWorkStatus.USER_EDIT, doc1.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.OTHER_EDIT, doc2.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.OTHER_VIEW, doc3.getOfflineWorkStatus());

    assertTrue(doc1.isSelectedForOfflineWork());
    assertFalse(doc2.isSelectedForOfflineWork());
    assertFalse(doc3.isSelectedForOfflineWork());

    // from offlineUser2 perspective
    logoutAndLoginAs(offlineUser2);
    offlineManager.loadOfflineWorkStatusOfRecords(docs, offlineUser2);
    assertEquals(OfflineWorkStatus.USER_VIEW, doc1.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.USER_EDIT, doc2.getOfflineWorkStatus());
    assertEquals(OfflineWorkStatus.USER_VIEW, doc3.getOfflineWorkStatus());

    assertTrue(doc1.isSelectedForOfflineWork());
    assertTrue(doc2.isSelectedForOfflineWork());
    assertTrue(doc3.isSelectedForOfflineWork());
  }

  private void shareRecordWithOfflineGroup(Record record, String permission) {
    ShareConfigElement gsCommand = new ShareConfigElement(offlineGroup.getId(), permission);
    recordSharingMgr.shareRecord(
        offlineUser1, record.getId(), new ShareConfigElement[] {gsCommand});
  }
}
