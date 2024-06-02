package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.comms.MessageType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MessageAndNotificationTrackerTest {
  MessageAndNotificationTracker tracker = null;

  @Before
  public void setUp() throws Exception {
    tracker = new MessageAndNotificationTracker();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testChangeUserMessageCount() {
    // standard message
    tracker.updateMessageCount(1L, 1, MessageType.SIMPLE_MESSAGE);
    assertEquals(1, tracker.getMessageCountFor(1L).intValue());
    assertEquals(0, tracker.getSpecialMessageCountFor(1L).intValue());

    // special message
    tracker.updateMessageCount(1L, 1, MessageType.REQUEST_CREATE_LAB_GROUP);
    assertEquals(1, tracker.getMessageCountFor(1L).intValue());
    assertEquals(1, tracker.getSpecialMessageCountFor(1L).intValue());
  }

  @Test
  public void testChangeUserNotificationCount() {
    Long USER1 = 1L;
    Long USER2 = 2L;
    assertFalse(tracker.userHasNewNotifications(USER1));
    tracker.changeUserNotificationCount(USER1, 10);
    assertTrue(tracker.userHasNewNotifications(USER1));
    assertFalse(tracker.userHasNewNotifications(USER2));

    // remove 10 notifications
    tracker.changeUserNotificationCount(USER1, -9);
    assertTrue(tracker.userHasNewNotifications(USER1));
    tracker.changeUserNotificationCount(USER1, -1);
    assertFalse(tracker.userHasNewNotifications(USER1));

    tracker.changeUserNotificationCount(USER1, 10);
    assertTrue(tracker.userHasNewNotifications(USER1));
    // clear removes all
    tracker.clearUserNotificationCount(USER1);
    assertFalse(tracker.userHasNewNotifications(USER1));
  }

  @Test
  public void testClearUserNotificationCount() {
    // check ok to call this if there is no count associated with user yet
    tracker.clearUserNotificationCount(3L);
  }
}
