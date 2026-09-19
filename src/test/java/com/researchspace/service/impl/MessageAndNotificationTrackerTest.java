package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.comms.MessageType;
import com.researchspace.webapp.messaging.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
public class MessageAndNotificationTrackerTest {

  @Mock SimpMessagingTemplate messagingTemplate;

  @InjectMocks MessageAndNotificationTracker tracker;

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

  @Test
  public void testNotificationServiceCalledOnNewNotification() {
    Long userId = 1L;
    tracker.changeUserNotificationCount(userId, 1);

    Mockito.verify(messagingTemplate)
        .convertAndSend("/topic/notifications/1", new NotificationMessage(1, 0, 0));
  }
}
