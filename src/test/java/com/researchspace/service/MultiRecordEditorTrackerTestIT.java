package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.testutil.SequencedRunnableRunner;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class MultiRecordEditorTrackerTestIT extends RealTransactionSpringTestBase {

  private ShiroTestUtils shiroUtils;

  private Subject subjectUnderTest;

  @Autowired private RecordEditorTracker tracker;

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

  private void logout() {
    // Subject subject = shiroUtils.getSubject();  CANT GET SUBJECT USING SHIROUTILS
    subjectUnderTest.logout();
  }

  /**
   * Add to security-test.xml (securityManagerTest) the property defined in security.xml <property
   * name="sessionManager">.
   *
   * @throws Exception
   */
  @Test
  public void editRecordLogoutSameUserTest() throws Exception {

    final int n = 5, numThreads = 3;
    final int t0 = 0, t1 = 1, t2 = 2;
    final User user1 = createAndSaveUser("shiro1" + RandomStringUtils.randomAlphabetic(n));
    initUsers(user1);

    shiroUtils.doLogin(user1);
    final StructuredDocument sd = createBasicDocumentInRootFolderWithText(user1, "some text");

    log.info("\neditRecordLogoutSameUserTest started");
    Invokable[] invokables = new Invokable[numThreads];
    invokables[t0] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          log.info("editRecordLogoutSameUserTest Thread 1 finished");
        };

    invokables[t1] =
        () -> {
          logout();
          log.info("editRecordLogoutSameUserTest Thread 2 finished");
        };
    invokables[t2] =
        () -> {
          // We check here if someone is editing the record.
          assertEquals(tracker.getEditingUserForRecord(sd.getId()), null);
          log.info("editRecordLogoutSameUserTest Thread 3 finished");
        };

    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {t0, t2});
    config.put("t2", new Integer[] {t1});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    log.info("editRecordLogoutSameUserTest finished \n");
  }

  @Test
  public void editRecordSameUserTest() throws Exception {

    final int n = 5, numThreads = 4;
    final int t0 = 0, t1 = 1, t2 = 2, t3 = 3;

    final String content = RandomStringUtils.randomAlphanumeric(10);
    final String content2 = RandomStringUtils.randomAlphanumeric(10);
    final User user1 = createAndSaveUser("shiro1" + RandomStringUtils.randomAlphabetic(n));
    initUsers(user1);

    shiroUtils.doLogin(user1);
    final StructuredDocument sd = createBasicDocumentInRootFolderWithText(user1, "some text");

    log.info("\neditRecordSameUserTest started");
    Invokable[] invokables = new Invokable[numThreads];
    invokables[t0] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          sd.getFields().get(0).setData(content);
          recordMgr.saveStructuredDocument(sd.getId(), user1.getUsername(), false, null);
          log.info("editRecordSameUserTest Thread 1 finished");
        };

    invokables[t1] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          String field = sd.getFields().get(0).getData();
          assertEquals(field, content);
          log.info("editRecordSameUserTest Thread 2 finished");
        };

    invokables[t2] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          sd.getFields().get(0).setData(content2);
          recordMgr.saveStructuredDocument(sd.getId(), user1.getUsername(), false, null);
          log.info("editRecordSameUserTest Thread 3 finished");
        };

    invokables[t3] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          String field = sd.getFields().get(0).getData();
          assertEquals(field, content2);
          log.info("editRecordSameUserTest Thread 4 finished");
        };

    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {t0, t2});
    config.put("t2", new Integer[] {t1, t3});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    log.info("editRecordSameUserTest finished \n");
  }

  @Test
  public void editSharedRecordDiferentUsersTest() throws Exception {

    final int numThreads = 4;
    final int t0 = 0, t1 = 1, t2 = 2, t3 = 3;
    final GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();

    log.info("editSharedRecordDiferentUsersTest started");
    Invokable[] invokables = new Invokable[numThreads];
    invokables[t0] =
        () -> {
          shiroUtils.doLogin(piUser);
          EditStatus editStatus =
              recordMgr.requestRecordEdit(
                  setup.structuredDocument.getId(), piUser, anySessionTracker());
          assertEquals(editStatus, EditStatus.EDIT_MODE);
          setup.structuredDocument.getFields().get(0).setData("Thread 1");
          assertEquals(setup.structuredDocument.getTextFields().get(0).getData(), "Thread 1");
          log.info("editSharedRecordDiferentUsersTest Thread 1 finished");
        };

    invokables[t1] =
        () -> {
          shiroUtils.doLogin(setup.user);
          UserSessionTracker tracker = anySessionTracker();
          tracker.addUser(piUser.getUsername(), new MockHttpSession());
          EditStatus editStatus =
              recordMgr.requestRecordEdit(
                  setup.structuredDocument.getId(), setup.user, activeUsers);
          assertEquals(editStatus, EditStatus.CANNOT_EDIT_OTHER_EDITING);
          log.info("editSharedRecordDiferentUsersTest Thread 2 finished");
        };

    invokables[t2] =
        () -> {
          Field field = setup.structuredDocument.getFields().get(0);
          recordMgr.saveTemporaryDocument(field, piUser, "Thread 1");
          recordMgr.saveStructuredDocument(
              setup.structuredDocument.getId(), piUser.getUsername(), true, null);
          assertEquals(setup.structuredDocument.getTextFields().get(0).getData(), "Thread 1");
          assertEquals(tracker.getEditingUserForRecord(setup.structuredDocument.getId()), null);
          log.info("editSharedRecordDiferentUsersTest Thread 3 finished");
        };

    invokables[t3] =
        () -> {
          EditStatus editStatus =
              recordMgr.requestRecordEdit(
                  setup.structuredDocument.getId(), setup.user, anySessionTracker());
          assertEquals(editStatus, EditStatus.EDIT_MODE);
          assertEquals(setup.structuredDocument.getTextFields().get(0).getData(), "Thread 1");
          log.info("editSharedRecordDiferentUsersTest Thread 4 finished");
        };

    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {t0, t2});
    config.put("t2", new Integer[] {t1, t3});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    log.info("editSharedRecordDiferentUsersTest finished \n");
  }

  @Test
  public void editDeleteRecordSameUserTest() throws Exception {

    // initialise all the users we need before starting tests
    final int n = 5, numThreads = 4;
    final int t0 = 0, t1 = 1, t2 = 2, t3 = 3;
    final User user1 = createAndSaveUser("shiro1" + RandomStringUtils.randomAlphabetic(n));
    initUsers(user1);

    shiroUtils.doLogin(user1);
    final StructuredDocument sd = createBasicDocumentInRootFolderWithText(user1, "some text");

    log.info("\neditDeleteRecordSameUserTest started");
    Invokable[] invokables = new Invokable[numThreads];
    invokables[t0] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          log.info("editDeleteRecordSameUserTest Thread 1 finished");
        };

    invokables[t1] =
        () -> {
          try {
            EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
            assertEquals(es, EditStatus.EDIT_MODE);
            recordDeletionMgr.deleteRecord(sd.getParent().getId(), sd.getId(), user1);
          } catch (Exception e) {
            log.info("This document is currently being edited");
          } finally {
            recordMgr.unlockRecord(sd.getId(), user1.getUsername());
          }

          log.info("editDeleteRecordSameUserTest Thread 2 finished");
        };

    invokables[t2] =
        () -> {
          recordMgr.unlockRecord(sd.getId(), user1.getUsername());
          assertEquals(tracker.getEditingUserForRecord(sd.getId()), null);
          log.info("editDeleteRecordSameUserTest Thread 3 finished");
        };

    invokables[t3] =
        () -> {
          try {
            EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
            assertEquals(es, EditStatus.EDIT_MODE);
            recordDeletionMgr.deleteRecord(sd.getParent().getId(), sd.getId(), user1);
            log.info("This document is been deleted successfully");
          } catch (Exception e) {
            log.info("This document is currently being edited");
          } finally {
            recordMgr.unlockRecord(sd.getId(), user1.getUsername());
          }

          log.info("editDeleteRecordSameUserTest Thread 4 finished");
        };

    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {t0, t2});
    config.put("t2", new Integer[] {t1, t3});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    log.info("editDeleteRecordSameUserTest finished \n");
  }

  @Test
  public void editAndSignRecordSameUserTest() throws Exception {
    final int n = 5, numThreads = 4;
    final int t0 = 0, t1 = 1, t2 = 2, t3 = 3;
    final User user1 = createAndSaveUser("shiro1" + RandomStringUtils.randomAlphabetic(n));
    initUsers(user1);

    shiroUtils.doLogin(user1);
    final StructuredDocument sd = createBasicDocumentInRootFolderWithText(user1, "some text");

    log.info("\neditAndSignRecordSameUserTest started");
    Invokable[] invokables = new Invokable[numThreads];
    invokables[t0] =
        () -> {
          EditStatus es = recordMgr.requestRecordEdit(sd.getId(), user1, anySessionTracker());
          assertEquals(es, EditStatus.EDIT_MODE);
          log.info("editAndSignRecordSameUserTest Thread 1 finished");
        };

    invokables[t1] =
        () -> {
          boolean result =
              signingManager.signRecord(sd.getId(), user1, null, "statement").isSuccessful();
          assertFalse(result);
          log.info("editAndSignRecordSameUserTest Thread 2 finished");
        };

    invokables[t2] =
        () -> {
          recordMgr.unlockRecord(sd, user1);
          assertEquals(tracker.getEditingUserForRecord(sd.getId()), null);
          log.info("editAndSignRecordSameUserTest Thread 3 finished");
        };

    invokables[t3] =
        () -> {
          boolean result =
              signingManager.signRecord(sd.getId(), user1, null, "statement").isSuccessful();
          assertTrue(result);
          log.info("editAndSignRecordSameUserTest Thread 4 finished");
        };

    Map<String, Integer[]> config = new TreeMap<>();
    config.put("t1", new Integer[] {t0, t2});
    config.put("t2", new Integer[] {t1, t3});
    SequencedRunnableRunner runner = new SequencedRunnableRunner(config, invokables);
    runner.runSequence();
    log.info("editAndSignRecordSameUserTest finished \n");
  }
}
