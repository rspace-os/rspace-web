package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class DeletionTestAcceptanceIT extends RealTransactionSpringTestBase {

  private static final String EDITING_SESSION_ID = "deletion-test-editing-session";

  @Autowired RecordDeletionManager deleter;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testSomeoneCanDeleteWhileAnotherUserEditing() throws Exception {
    TestGroup tg = createTestGroup(2);

    logoutAndLoginAs(tg.u2());
    Folder u2Root = folderMgr.getRootRecordForUser(tg.u2(), tg.u2());
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(tg.u2(), "text");
    ShareConfigElement cnd = new ShareConfigElement(tg.getGroup().getId(), "edit");
    sharingMgr.shareRecord(tg.u2(), doc1.getId(), new ShareConfigElement[] {cnd});

    logoutAndLoginAs(tg.u1());
    assertEquals(
        EditStatus.EDIT_MODE,
        recordMgr.requestRecordEdit(
            doc1.getId(), tg.u1(), getUsernames(tg.u1()), () -> EDITING_SESSION_ID));

    logoutAndLoginAs(tg.u2());
    assertExceptionThrown(
        () -> deleter.deleteRecord(u2Root.getId(), doc1.getId(), tg.u2()),
        DocumentAlreadyEditedException.class);

    logoutAndLoginAs(tg.u1());
    recordMgr.unlockRecord(doc1.getId(), tg.u1().getUsername(), () -> EDITING_SESSION_ID);

    logoutAndLoginAs(tg.u2());
    deleter.deleteRecord(u2Root.getId(), doc1.getId(), tg.u2());
    assertTrue(recordMgr.get(doc1.getId()).isDeleted());
  }

  private UserSessionTracker getUsernames(User... users) {
    UserSessionTracker tr = anySessionTracker();
    for (User u : users) {
      tr.addUser(u.getUsername(), new MockHttpSession());
    }
    return tr;
  }

  @Test
  public void testPostDeleteNotifications() throws Exception {
    TestGroup grp = createTestGroup(2);
    setUpMessagePreferences(grp);
    User u1 = grp.u1();
    logoutAndLoginAs(u1);
    Folder f1 = createSubFolder(getRootFolderForUser(u1), "f1", u1);
    StructuredDocument doc1 = createBasicDocumentInFolder(u1, f1, "doc1");
    StructuredDocument doc2 = createBasicDocumentInFolder(u1, f1, "doc2");
    StructuredDocument doc3 = createBasicDocumentInFolder(u1, f1, "doc3");
    shareRecordWithGroup(u1, grp.getGroup(), doc1);
    shareRecordWithGroup(u1, grp.getGroup(), doc2);
    shareRecordWithGroup(u1, grp.getGroup(), doc3);

    final int initialPiCount = getNewNotificationCount(grp.getPi());
    final int initialU2Count = getNewNotificationCount(grp.u2());

    // delete shared doc1. This will notify u2(unshared) and pi(deleted)
    deleter.deleteRecord(f1.getId(), doc1.getId(), u1);
    assertEquals(initialPiCount + 1, getNewNotificationCount(grp.getPi()));
    assertEquals(initialU2Count + 1, getNewNotificationCount(grp.u2()));
    assertHasNotification(
        grp.getPi(), NotificationType.NOTIFICATION_DOCUMENT_DELETED, doc1.getId());
    assertHasNotification(grp.u2(), NotificationType.NOTIFICATION_DOCUMENT_UNSHARED, doc1.getId());

    // delete folder. This will notify pi (folder) and u2 twice ( for 2 unshared docs)
    deleter.deleteFolder(getRootFolderForUser(u1).getId(), f1.getId(), u1);
    assertEquals(initialPiCount + 2, getNewNotificationCount(grp.getPi()));
    assertEquals(initialU2Count + 3, getNewNotificationCount(grp.u2()));
    assertHasNotification(grp.getPi(), NotificationType.NOTIFICATION_DOCUMENT_DELETED, f1.getId());
    assertHasNotification(grp.u2(), NotificationType.NOTIFICATION_DOCUMENT_UNSHARED, doc2.getId());
    assertHasNotification(grp.u2(), NotificationType.NOTIFICATION_DOCUMENT_UNSHARED, doc3.getId());
  }

  private void assertHasNotification(User recipient, NotificationType type, Long recordId) {
    boolean found =
        communicationMgr
            .getNewNotificationsForUser(
                recipient.getUsername(),
                PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
            .getResults()
            .stream()
            .anyMatch(
                notification ->
                    type == notification.getNotificationType()
                        && notification.getRecord() != null
                        && recordId.equals(notification.getRecord().getId()));
    assertTrue(found, () -> "Missing " + type + " notification for record " + recordId);
  }
}
