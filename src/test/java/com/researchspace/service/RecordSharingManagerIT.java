package com.researchspace.service;

import static com.researchspace.model.PaginationCriteria.createDefaultForClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This is for tests with object relations and multiple transactions that are too complicated to run
 * in a {@link SpringTransactionalTest}
 */
public class RecordSharingManagerIT extends RealTransactionSpringTestBase {

  private @Autowired RecordGroupSharingDao groupShareDao;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  // RSPAC1093
  @Test
  public void RSPAC193copiesOfSharedDocumentsAreOnlyViewbleByOwner() {
    TestGroup group = createTestGroup(2);
    User u1 = group.getUserByPrefix("u1");
    User u2 = group.getUserByPrefix("u2");
    logoutAndLoginAs(u1);
    StructuredDocument originalDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    shareRecordWithGroup(u1, group.getGroup(), originalDoc);
    RecordCopyResult copyResult =
        recordMgr.copy(originalDoc.getId(), "copy", u1, getRootFolderForUser(u1).getId());
    StructuredDocument copyDoc = copyResult.getCopy(originalDoc).asStrucDoc();
    // logout and login as u2. shouldn't see copy:
    logoutAndLoginAs(u2);
    assertTrue(
        "u2 should be able to read 'originalDoc' as was shared with him",
        permissionUtils.isPermitted(originalDoc, PermissionType.READ, u2));
    assertFalse(
        "u2 should  NOT be be able to read copy",
        permissionUtils.isPermitted(copyDoc, PermissionType.READ, u2));
    logoutAndLoginAs(u1);
    assertTrue("ACLs are missing from copy", copyDoc.getSharingACL().isACLPopulated());
    assertTrue(
        "Owner (u1) should be able to read the copy",
        permissionUtils.isPermitted(copyDoc, PermissionType.READ, u1));
  }

  // RSPAC-930
  @Test
  public void shareUnshareTemplatesWithAttachmentsStillViewable()
      throws FileNotFoundException, Exception {
    TestGroup group = createTestGroup(2);
    User u1 = group.getUserByPrefix("u1");
    User u2 = group.getUserByPrefix("u2");

    // addUsersToGroup(pi, labGroup, u1, u2);
    logoutAndLoginAs(u1);
    StructuredDocument originalDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    Field field = originalDoc.getFields().get(0);
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), field, u1);
    // sanity check
    assertTrue(permissionUtils.isPermitted(attachment, PermissionType.READ, u1));
    logoutAndLoginAs(u2);
    assertFalse(permissionUtils.isPermitted(attachment, PermissionType.READ, u2));
    logoutAndLoginAs(u1);
    openTransaction();
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(originalDoc.getId(), u1);
    commitTransaction();
    // flushDatabaseState();

    shareRecordWithGroup(u1, group.getGroup(), template);
    // now we'll add a second attachment *after* the initial share.
    Long templatefieldId = template.getFields().get(0).getId();
    Field templateField = fieldMgr.get(templatefieldId, u1).get();
    EcatDocumentFile attachment2 =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), templateField, u1);

    logoutAndLoginAs(u2);

    attachment = (EcatDocumentFile) recordMgr.get(attachment.getId()); // refresh
    attachment2 = (EcatDocumentFile) recordMgr.get(attachment2.getId()); // refresh
    assertTrue(permissionUtils.isPermitted(attachment, PermissionType.READ, u2));
    assertTrue(permissionUtils.isPermitted(attachment2, PermissionType.READ, u2));
    assertTrue(permissionUtils.isPermitted(template, PermissionType.READ, u2));

    logoutAndLoginAs(u1);
    unshareRecordORNotebookWithGroup(u1, template, group.getGroup(), "read");
    logoutAndLoginAs(u2);
    attachment = (EcatDocumentFile) recordMgr.get(attachment.getId()); // refresh
    attachment2 = (EcatDocumentFile) recordMgr.get(attachment2.getId()); // refresh
    assertTrue(permissionUtils.isPermitted(attachment, PermissionType.READ, u2));
    assertTrue(permissionUtils.isPermitted(attachment2, PermissionType.READ, u2));
    template = recordMgr.get(template.getId()).asStrucDoc();
    assertFalse(permissionUtils.isPermitted(template, PermissionType.READ, u2));
  }

  @Test
  public void labadminCanDeleteSharedSubfolders_RSPAC1035()
      throws FileNotFoundException, Exception {
    TestGroup group = createTestGroup(1, new TestGroupConfig(true));

    logoutAndLoginAs(group.getPi());
    // pi shares a document and moves it into shared subfolder
    Folder groupSharedFolder = getGroupSharedFolder(group.getGroup());
    Folder sharedSub1 = createSubFolder(groupSharedFolder, "SharedSub1", group.getPi());
    Folder sharedSub2 = createSubFolder(sharedSub1, "SharedSub2", group.getPi());
    Folder sharedSub2a = createSubFolder(sharedSub1, " =", group.getPi());
    Folder piHomFolder = folderMgr.getRootFolderForUser(group.getPi());
    Notebook nb = createNotebookWithNEntries(piHomFolder.getId(), "Notebook", 3, group.getPi());
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(group.getPi(), "any");
    shareRecordWithGroup(group.getPi(), group.getGroup(), toShare);
    shareNotebookWithGroup(group.getPi(), nb, group.getGroup(), "read");
    // move within shared folder tree into subfolders
    recordMgr.move(toShare.getId(), sharedSub2a.getId(), groupSharedFolder.getId(), group.getPi());
    folderMgr.move(nb.getId(), sharedSub2.getId(), groupSharedFolder.getId(), group.getPi());

    // now login as labdAdmin and try to delete folder:
    User labAdmin = group.getUserByPrefix("labAdmin");
    logoutAndLoginAs(labAdmin);
    toShare = recordMgr.get(toShare.getId()).asStrucDoc();
    labAdmin = userMgr.get(labAdmin.getId()); // refresh user details
    CompositeRecordOperationResult res =
        recordDeletionMgr.deleteFolder(groupSharedFolder.getId(), sharedSub1.getId(), labAdmin);
    assertEquals(5, res.getRecords().size());

    // now assert that notebook entries are intact and all items are still parented in PIs folder:
    logoutAndLoginAs(group.getPi());
    nb = folderMgr.getNotebook(nb.getId());
    toShare = recordMgr.get(toShare.getId()).asStrucDoc();
    assertEquals(3, nb.getEntryCount());
    assertEquals(piHomFolder, nb.getParent());
    assertEquals(piHomFolder, toShare.getParent());

    // also assert that notebook and document are unshared after deletion:
    assertTrue("Notebook is still shared", sharingMgr.getRecordSharingInfo(nb.getId()).isEmpty());
    assertTrue(
        "Document is still shared", sharingMgr.getRecordSharingInfo(toShare.getId()).isEmpty());

    // also check that shared folder root has no visible subfolders:
    ISearchResults<BaseRecord> results =
        recordMgr.listFolderRecords(groupSharedFolder.getId(), getDefaultRecordPageCriteria());
    assertTrue("Items were not deleted", results.getHits() == 0);
  }

  @Test // IE PI can publish record shared by group member
  public void piCanShareGroupMembersRecordWithAnonymousUser() {
    TestGroup g1 = createTestGroup(3);
    setUpMessagePreferences(g1);

    User u1 = g1.u1();
    User pi = g1.getPi();
    logoutAndLoginAs(u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "any");
    shareRecordWithGroup(u1, g1.getGroup(), doc1);
    publishDocumentForUser(pi, doc1.getId());
    ISearchResults<Notification> newNots =
        communicationMgr.getNewNotificationsForUser(
            RecordGroupSharing.ANONYMOUS_USER,
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(0, newNots.getResults().size());
    ISearchResults<RecordGroupSharing> rgsU1 =
        sharingMgr.listUserRecordsPublished(
            u1, PaginationCriteria.createDefaultForClass(RecordGroupSharing.class));
    assertNotNull(rgsU1.getFirstResult().getPublicLink());
    ISearchResults<RecordGroupSharing> rgsPI =
        sharingMgr.listPublishedRecordsOwnedByUserOrGroupMembersPlusRecordsPublishedByUser(
            pi, PaginationCriteria.createDefaultForClass(RecordGroupSharing.class));
    assertNotNull(rgsPI.getFirstResult().getPublicLink());
  }

  @Test
  public void testShareMultipleTimes_RSPAC2159() {
    TestGroup g1 = createTestGroup(3);
    setUpMessagePreferences(g1);

    User u1 = g1.u1();
    logoutAndLoginAs(u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "any");
    shareRecordWithGroup(u1, g1.getGroup(), doc1);
    // other group members just get original share
    assertEquals(1, getNewNotificationCount(g1.u3()));
    assertEquals(1, getNewNotificationCount(g1.getPi()));
    assertEquals(1, getNewNotificationCount(g1.u2()));

    // now share with u2 only with edit permission
    shareRecordWithUserForEdit(u1, doc1, g1.u2());
    // no notifications for sender
    assertEquals(0, getNewNotificationCount(u1));
    // u2 gets notified for both sharing events
    assertEquals(2, getNewNotificationCount(g1.u2()));
    // other group members just get original share
    assertEquals(1, getNewNotificationCount(g1.u3()));
    assertEquals(1, getNewNotificationCount(g1.getPi()));
  }

  @Test
  public void testShareUnshareNotifications_RSPAC1339() {
    TestGroup g1 = createTestGroup(3);
    setUpMessagePreferences(g1);

    User u1 = g1.u1();
    logoutAndLoginAs(u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "any");
    shareRecordWithGroup(u1, g1.getGroup(), doc1);
    // u2 and u3 should get notifications, also pi
    assertEquals(0, getNewNotificationCount(u1));
    assertEquals(1, getNewNotificationCount(g1.u2()));
    assertEquals(1, getNewNotificationCount(g1.u3()));
    assertEquals(1, getNewNotificationCount(g1.getPi()));

    unshareRecordORNotebookWithGroup(u1, doc1, g1.getGroup(), "read");
    // other users get notified of unshare
    assertEquals(2, getNewNotificationCount(g1.u2()));
    assertEquals(2, getNewNotificationCount(g1.u3()));
    assertEquals(1, getNewNotificationCount(g1.getPi()));

    // share again, even though unread, users will get new notification, as state has changed again
    shareRecordWithGroup(u1, g1.getGroup(), doc1);
    assertEquals(3, getNewNotificationCount(g1.u2()));
    assertEquals(3, getNewNotificationCount(g1.u3()));
  }

  @Test
  public void testMoveIntoNotebook() throws Exception {
    TestGroup g1 = createTestGroup(1);
    User u1 = g1.u1();
    logoutAndLoginAs(u1);

    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "any");
    shareRecordWithGroup(u1, g1.getGroup(), doc1);

    Notebook nb = createNotebookWithNEntries(getRootFolderForUser(u1).getId(), "nb", 1, u1);
    shareNotebookWithGroup(u1, nb, g1.getGroup(), "read");

    ServiceOperationResult<BaseRecord> result =
        recordMgr.move(doc1.getId(), nb.getId(), getRootFolderForUser(u1).getId(), u1);
    assertTrue(result.isSucceeded());

    List<RecordGroupSharing> rgs =
        doInTransaction(() -> groupShareDao.getRecordGroupSharingsForRecord(doc1.getId()));
    assertEquals(1, rgs.size());
    ErrorList el =
        sharingMgr.updatePermissionForRecord(rgs.get(0).getId(), "edit", u1.getUsername());
    assertNull(el);

    RSpaceTestUtils.logout();
    logoutAndLoginAs(u1);
    List<RecordGroupSharing> rgs2 = sharingMgr.getRecordSharingInfo(doc1.getId());
    assertEquals(PermissionType.WRITE, rgs2.get(0).getPermType());
  }

  @Test
  public void rspac2127SimultaneousShare() throws Exception {
    TestGroup g1 = createTestGroup(2);
    User u1 = g1.u1();
    User u2 = g1.u2();
    logoutAndLoginAs(u1);
    Long sharedFolderId = g1.getGroup().getCommunalGroupFolderId();
    Folder targetFolder =
        createSubFolder(folderMgr.getFolder(sharedFolderId, u1), "share-target", u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "any");
    ShareConfigElement withGroup = new ShareConfigElement(g1.getGroup().getId(), "read");
    withGroup.setGroupFolderId(targetFolder.getId());

    ShareConfigElement withU2 = new ShareConfigElement();
    withU2.setUserId(u2.getId());
    withU2.setOperation("read");

    ServiceOperationResult<Set<RecordGroupSharing>> result =
        sharingMgr.shareRecord(u1, doc1.getId(), new ShareConfigElement[] {withGroup, withU2});
    assertEquals(2, result.getEntity().size());

    doc1 = recordMgr.get(doc1.getId()).asStrucDoc();
    assertTrue(doc1.getParentFolders().contains(targetFolder));
  }

  private PaginationCriteria<BaseRecord> getDefaultRecordPageCriteria() {
    return createDefaultForClass(BaseRecord.class);
  }
}
