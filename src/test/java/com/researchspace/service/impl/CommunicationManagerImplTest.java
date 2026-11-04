package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.NotificationPostCommitExecutor;
import com.researchspace.testutils.TestFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class CommunicationManagerImplTest {

  class CommunicationManagerImplTSS extends CommunicationManagerImpl {
    boolean notified = false;

    public Notification systemNotify(
        NotificationType notificationType, String msg, String recipientName, boolean broadcast) {
      notified = true;
      return null;
    }
  }

  @Mock Broadcaster broadcaster1;
  @Mock Broadcaster broadcaster2;
  @Mock CommunicationDao commDao;
  @Mock UserDao userDao;
  @Mock IMessageAndNotificationTracker notificnTracker;
  @Mock NotificationPostCommitExecutor notificationPostCommitExecutor;

  CommunicationManagerImplTSS mgr = new CommunicationManagerImplTSS();

  @Test
  public void testBroadcastWillUseAllBroadcastersEvenIfOneThrowsException() {
    mgr.setBroadcasters(Arrays.asList(new Broadcaster[] {broadcaster1, broadcaster2}));
    Mockito.doThrow(RuntimeException.class)
        .when(broadcaster1)
        .broadcast(Mockito.any(Communication.class));

    User sender = TestFactory.createAnyUser("s");
    User recip = TestFactory.createAnyUser("recip");

    MessageOrRequest mor = TestFactory.createAnyMessageForRecipuent(sender, recip);
    mgr.broadcast(mor, Collections.EMPTY_SET);
    Mockito.verify(broadcaster2, Mockito.times(1)).broadcast(Mockito.any(Communication.class));
    assertTrue(mgr.notified);
  }

  @Test
  public void notifyDeliversImmediatelyWithoutTransactionSynchronization() {
    ReflectionTestUtils.setField(mgr, "commDao", commDao);
    ReflectionTestUtils.setField(mgr, "userDao", userDao);
    ReflectionTestUtils.setField(mgr, "notificnTracker", notificnTracker);
    ReflectionTestUtils.setField(
        mgr, "notificationPostCommitExecutor", notificationPostCommitExecutor);
    mgr.setBroadcasters(Collections.singletonList(broadcaster1));
    User originator = TestFactory.createAnyUser("originator");
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(1L);
    Mockito.when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);

    mgr.notify(
        originator,
        null,
        NotificationConfig.builder()
            .notificationType(NotificationType.NOTIFICATION_DOCUMENT_SHARED)
            .broadcast(true)
            .policyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY)
            .notificationTargetsOverride(new HashSet<>(Set.of(recipient)))
            .build(),
        "message");

    Mockito.verify(broadcaster1).broadcast(Mockito.any(Communication.class));
    Mockito.verify(notificnTracker).changeUserNotificationCount(recipient.getId(), 1);
    Mockito.verifyNoInteractions(notificationPostCommitExecutor);
  }
}
