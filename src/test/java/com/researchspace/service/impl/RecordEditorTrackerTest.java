package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.session.UserSessionTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;

public class RecordEditorTrackerTest {

  Supplier<String> sessionIDPRovider = () -> "sessionId";

  private Record r1, r2, r3;
  private User u1, u2, u3;
  private RecordEditorTracker tracker = new RecordEditorTracker();
  private UserSessionTracker activeusers = new UserSessionTracker();
  private Map<Long, String> reporter = new ConcurrentHashMap<Long, String>();

  private List<String> msges = Collections.synchronizedList(new ArrayList<String>());

  private boolean failedDueToUserRemoved = false;
  private boolean tooManyViewers = false;

  @Before
  public void setUp() throws Exception {
    // test fixture = 3 records and 3 users, each record created by a
    // different user.
    final long r1Id = 1L;
    final long r2Id = 2L;
    final long r3Id = 3L;

    u1 = TestFactory.createAnyUser("a");
    u2 = TestFactory.createAnyUser("b");
    u3 = TestFactory.createAnyUser("c");
    r1 = TestFactory.createAnySD();
    r1.setCreatedBy(u1.getUsername());
    r1.setId(r1Id);
    r2 = TestFactory.createAnySD();
    r2.setId(r2Id);
    r2.setCreatedBy(u2.getUsername());
    r3 = TestFactory.createAnySD();
    r3.setCreatedBy(u3.getUsername());
    r3.setId(r3Id);
    // represents logged in users.
    activeusers.addUser(u1.getUsername(), new MockHttpSession());
    activeusers.addUser(u2.getUsername(), new MockHttpSession());
    activeusers.addUser(u3.getUsername(), new MockHttpSession());
    // u3 is not an active user
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void isSomeoneElseEditing() {
    // initial state
    assertFalse(tracker.isSomeoneElseEditing(r1, u2.getUsername()));
    // u1 is editing
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u1, activeusers));
    // false, editor is u1
    assertFalse(tracker.isSomeoneElseEditing(r1, u1.getUsername()));
    // true, from u2's point of view
    assertTrue(tracker.isSomeoneElseEditing(r1, u2.getUsername()));
    // u1 stops editing
    tracker.unlockRecord(r1, u1, sessionIDPRovider);
    // now from  u2's point of view this is false again
    assertFalse(tracker.isSomeoneElseEditing(r1, u2.getUsername()));
  }

  @Test
  public void testAttemptToEditAllowsOnlyOneUserToEditRecord() {
    // u1 can edit; no-one is editing anything yet
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u1, activeusers));
    // u1 can also edit a document he already has access to
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u1, activeusers));
    // u2 can't edit r1
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, attemptEdit(r1.getId(), u2, activeusers));
    // but u2 can edit r2
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r2.getId(), u2, activeusers));
    // so u1 can't edit r2
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, attemptEdit(r2.getId(), u1, activeusers));
  }

  private EditStatus attemptEdit(Long recordId, User user, UserSessionTracker activeusers) {
    return tracker.attemptToEdit(recordId, user, activeusers, sessionIDPRovider);
  }

  @Test
  public void testLoggedOutUserGivesUpAccessToRecord() {
    // u1 can edit; no-one is editing anything yet
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u1, activeusers));
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, attemptEdit(r1.getId(), u3, activeusers));

    // now remove u1 from active userlist
    // activeusers.remove(u1.getUsername());
    tracker.unlockRecord(r1, u1, sessionIDPRovider);

    // u3 can now access the document
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u3, activeusers));
  }

  @Test
  public void testUnlockRecord() {
    attemptEdit(r1.getId(), u1, activeusers);
    tracker.unlockRecord(r1, u1, sessionIDPRovider);
    // now is unlocked, another user can edit
    assertEquals(EditStatus.EDIT_MODE, attemptEdit(r1.getId(), u2, activeusers));

    // another user can't unlock a record owned by a user
    tracker.unlockRecord(r1, u1, sessionIDPRovider);
    assertEquals(EditStatus.CANNOT_EDIT_OTHER_EDITING, attemptEdit(r1.getId(), u1, activeusers));
    assertEquals(u2.getUsername(), tracker.isEditing(r1).get());
  }

  @Test
  public void testSynchronizedAccess() throws InterruptedException {
    RecordAccessThread t1 = new RecordAccessThread(u1);
    RecordAccessThread t2 = new RecordAccessThread(u2);
    RecordAccessThread t3 = new RecordAccessThread(u3);
    Thread th1 = new Thread(t1);
    Thread th2 = new Thread(t2);
    Thread th3 = new Thread(t3);

    th1.start();
    th2.start();
    th3.start();
    th1.join();
    th2.join();
    th3.join();
    assertFalse(tooManyViewers && failedDueToUserRemoved);
  }

  /** Simulates many users trying to lock/unlock records for editing. */
  final class RecordAccessThread implements Runnable {
    private User user;

    private RecordAccessThread(User user) {
      super();
      this.user = user;
    }

    @Override
    public void run() {

      final int limit = 5000;
      final int n10 = 10;
      final int n4 = 4;
      for (int i = 0; i < limit; i++) {

        Record r = getARandomRecord();
        int random = new Random().nextInt(n10);
        String editor = tracker.isEditing(r).get();
        String username = user.getUsername();

        if (editor != null && username.equals(editor)) {
          // unlock 1in 10 chance
          if (random == 0) {
            tracker.unlockRecord(r, user, sessionIDPRovider);
            msges.add(username + " unlocking " + r.getId());
          }
          // attempt to edit 4 in 10 chance
        } else if (random < n4) {

          EditStatus es = attemptEdit(r.getId(), user, activeusers);
          if (es.equals(EditStatus.EDIT_MODE)) {
            synchronized (reporter) {
              assertTrue(reporter.get(r) == null || reporter.get(r).equals(username));
              reporter.put(r.getId(), user.getUsername());
            }
          } else {
            if (!tracker.getViewersForRecord(r.getId()).contains(username)) {
              tracker.addViewerToRecord(r, user.getUsername());

              if (!tracker.getViewersForRecord(r.getId()).contains(username)) {
                failedDueToUserRemoved = true;
              }
            } else {
              tracker.removeViewerFromRecord(r, username);
            }
          }
        }
        final int numUsers = 3;
        if (tracker.getViewersForRecord(r.getId()).size() > numUsers) {
          tooManyViewers = true;
        }
      }
    }
  }

  Record getARandomRecord() {
    final int n = 3;
    int random = new Random().nextInt(n);
    if (random == 0) {
      return r1;
    } else if (random == 1) {
      return r2;
    } else {
      return r3;
    }
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testGetViewersForRecord() {
    Set<String> viewers = tracker.getViewersForRecord(r1.getId());
    assertNotNull(viewers); // should not return null
    assertTrue(viewers.isEmpty());
    viewers.add("new user"); // fails! cannnot modify this directly
  }

  @Test
  public void testAddRemoveViewersForRecord() {
    tracker.addViewerToRecord(r1, u1.getUsername());
    assertEquals(1, tracker.getViewersForRecord(r1.getId()).size());
    assertEquals(u1.getUsername(), tracker.getViewersForRecord(r1.getId()).iterator().next());

    // add again - should ignore duplicates
    tracker.addViewerToRecord(r1, u1.getUsername());
    assertEquals(1, tracker.getViewersForRecord(r1.getId()).size());

    // should be no viewers now
    tracker.removeViewerFromRecord(r1, u1.getUsername());
    assertEquals(0, tracker.getViewersForRecord(r1.getId()).size());
  }

  @Test
  public void testNoNPEIfRemovingLoggedOutUser() {
    assertEquals("", tracker.unlockRecord(r1, null, sessionIDPRovider));
  }
}
