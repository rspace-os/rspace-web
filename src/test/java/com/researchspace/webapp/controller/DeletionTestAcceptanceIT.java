package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.testutil.SequencedRunnableRunner;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestGroup;
import java.util.Map;
import java.util.TreeMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class DeletionTestAcceptanceIT extends RealTransactionSpringTestBase {

  Logger log = LoggerFactory.getLogger(DeletionTestAcceptanceIT.class);

  ShiroTestUtils shiroUtils;
  @Autowired RecordDeletionManager deleter;

  @Before
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    shiroUtils.clearSubject();
  }

  @Test
  // test fails intermittently as occasionally the pi user disappears from group.userGroups which
  // causes the deletion
  // of the record to fail as notifying the pi user fails due. Tested manually and this issue is not
  // present so only
  // exists as a problem in this test. See RSDEV-99 on Jira.
  @Ignore
  public void testSomeoneCanDeleteWhileAnotherUserEditing() throws Exception {
    TestGroup tg = createTestGroup(2);

    logoutAndLoginAs(tg.u2());
    final Folder u2Root = folderMgr.getRootRecordForUser(tg.u2(), tg.u2());
    final StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(tg.u2(), "text");
    ShareConfigElement cnd = new ShareConfigElement(tg.getGroup().getId(), "edit");
    sharingMgr.shareRecord(tg.u2(), doc1.getId(), new ShareConfigElement[] {cnd});
    RSpaceTestUtils.logout();
    // these are the steps to execute:

    Invokable u1StartsEditing =
        () -> {
          log.info("u1StartsEditing");
          shiroUtils.doLogin(tg.u1());
          assertEquals(
              EditStatus.EDIT_MODE,
              recordMgr.requestRecordEdit(doc1.getId(), tg.u1(), getUsernames(tg.u1())));
        };

    Invokable u2AttemptsToDeleteButCannot =
        () -> {
          log.info("u2AttemptsToDeleteButCannot");
          shiroUtils.doLogin(tg.u2());
          assertExceptionThrown(
              () -> deleter.deleteRecord(u2Root.getId(), doc1.getId(), tg.u2()),
              DocumentAlreadyEditedException.class);
        };
    Invokable u1StopsEditing =
        () -> {
          log.info("u1StopsEditing");
          recordMgr.unlockRecord(doc1, tg.u1());
          RSpaceTestUtils.logout();
          log.info("u1LogsOut");
        };
    Invokable u2SuccessfullyDeletes =
        () -> {
          log.info("u2SuccessfullyDeletes");
          deleter.deleteRecord(u2Root.getId(), doc1.getId(), tg.u2());
          RSpaceTestUtils.logout();
          log.info("u2LogsOut");
        };
    Invokable[] actions =
        new Invokable[] {
          u1StartsEditing, u2AttemptsToDeleteButCannot, u1StopsEditing, u2SuccessfullyDeletes
        };
    Map<String, Integer[]> config = new TreeMap<>();
    config.put("u1", new Integer[] {0, 2});
    config.put("u2", new Integer[] {1, 3});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, actions);
    runner.runSequence();
    assertNull(runner.getInternalException());
    assertTrue(runner.isCompletedOK());
  }

  private UserSessionTracker getUsernames(User... users) {
    UserSessionTracker tr = anySessionTracker();
    for (User u : users) {
      tr.addUser(u.getUsername(), new MockHttpSession());
    }
    return tr;
  }

  @Test
  @Ignore
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

    // delete folder. This will notify pi (folder) and u2 twice ( for 2 unshared docs)
    deleter.deleteFolder(getRootFolderForUser(u1).getId(), f1.getId(), u1);
    assertEquals(initialPiCount + 2, getNewNotificationCount(grp.getPi()));
    assertEquals(initialU2Count + 3, getNewNotificationCount(grp.u2()));
  }
}
