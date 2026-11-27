package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationNotifyPolicy;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.NotificationPostCommitExecutor;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
public class CommunicationManagerImplTest {

  class CommunicationManagerImplTSS extends CommunicationManagerImpl {
    boolean notified = false;
    boolean recordNotificationsOnly = false;
    final List<Set<User>> createdNotificationRecipients = new ArrayList<>();

    @Override
    public Notification doCreateNotification(
        NotificationType type,
        String originator,
        BaseRecord record,
        Set<User> recipients,
        String optionalhumanmessage,
        String systemMsg) {
      if (recordNotificationsOnly) {
        createdNotificationRecipients.add(recipients);
        return null;
      }
      return super.doCreateNotification(
          type, originator, record, recipients, optionalhumanmessage, systemMsg);
    }

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
  @Mock MessageSourceUtils messages;

  CommunicationManagerImplTSS mgr = new CommunicationManagerImplTSS();

  private void wireManager() {
    ReflectionTestUtils.setField(mgr, "commDao", commDao);
    ReflectionTestUtils.setField(mgr, "userDao", userDao);
    ReflectionTestUtils.setField(mgr, "notificnTracker", notificnTracker);
    ReflectionTestUtils.setField(
        mgr, "notificationPostCommitExecutor", notificationPostCommitExecutor);
  }

  @Test
  void cancellingGlobalMessageUpdatesCountsButCreatesNoNotification() {
    wireManager();
    ReflectionTestUtils.setField(mgr, "messages", messages);
    mgr.recordNotificationsOnly = true;
    User sysadmin = TestFactory.createAnyUser("sysadmin");
    User first = TestFactory.createAnyUser("first");
    first.setId(1L);
    User second = TestFactory.createAnyUser("second");
    second.setId(2L);
    MessageOrRequest globalMessage =
        TestFactory.createAnyMessageForRecipientOfType(sysadmin, first, MessageType.GLOBAL_MESSAGE);
    CommunicationTarget secondTarget = new CommunicationTarget();
    secondTarget.setRecipient(second);
    globalMessage.addRecipient(secondTarget);
    Mockito.when(commDao.get(10L)).thenReturn(globalMessage);

    mgr.cancelRequest("sysadmin", 10L, false);

    assertEquals(CommunicationStatus.CANCELLED, globalMessage.getStatus());
    globalMessage
        .getRecipients()
        .forEach(ct -> assertEquals(CommunicationStatus.CANCELLED, ct.getStatus()));
    Mockito.verify(commDao).save(globalMessage);
    Mockito.verify(notificnTracker).updateMessageCount(1L, -1, MessageType.GLOBAL_MESSAGE);
    Mockito.verify(notificnTracker).updateMessageCount(2L, -1, MessageType.GLOBAL_MESSAGE);
    assertThat(mgr.createdNotificationRecipients).isEmpty();
    Mockito.verifyNoInteractions(messages);
  }

  @Test
  void batchesMultiItemReadAndCleansUpAfterCompletion() {
    wireManager();
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(1L);
    Mockito.when(userDao.getUserByUsername("recipient")).thenReturn(recipient);
    for (long id = 1; id <= 5; id++) {
      Notification notification = new Notification();
      var target = new CommunicationTarget();
      target.setRecipient(recipient);
      notification.setRecipients(Set.of(target));
      Mockito.when(commDao.get(id)).thenReturn(notification);
    }
    Mockito.doAnswer(
            invocation -> {
              IntSupplier count = invocation.getArgument(1);
              count.getAsInt();
              return null;
            })
        .when(notificnTracker)
        .refreshNotificationCount(Mockito.eq(1L), Mockito.any());
    Mockito.doAnswer(
            invocation -> {
              Runnable query = invocation.getArgument(0);
              query.run();
              return null;
            })
        .when(notificationPostCommitExecutor)
        .execute(Mockito.any());
    TransactionSynchronizationManager.initSynchronization();
    try {
      mgr.markNotificationsAsRead(Set.of(1L, 2L, 3L, 4L, 5L), "recipient");
      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertEquals(1, callbacks.size());
      Mockito.verifyNoInteractions(notificnTracker);
      callbacks.forEach(TransactionSynchronization::afterCommit);
      Mockito.verify(notificnTracker).refreshNotificationCount(Mockito.eq(1L), Mockito.any());
      Mockito.verify(commDao).getUnreadNotificationCount(1L);
      Mockito.verify(commDao, Mockito.never()).getNotificationStatus(Mockito.any());
      callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
      assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void failedRecipientAndBroadcastDoNotSkipOtherRecipientsOrLaterCallbacks() {
    wireManager();
    User originator = TestFactory.createAnyUser("originator");
    User first = TestFactory.createAnyUser("first");
    first.setId(1L);
    User second = TestFactory.createAnyUser("second");
    second.setId(2L);
    Mockito.when(userDao.getUserByUsername("originator")).thenReturn(originator);
    Mockito.doThrow(new IllegalStateException("broadcast transaction failed"))
        .when(notificationPostCommitExecutor)
        .execute(Mockito.any());
    Mockito.doThrow(new IllegalStateException("recount failed"))
        .when(notificnTracker)
        .refreshNotificationCount(Mockito.eq(1L), Mockito.any());
    TransactionSynchronizationManager.initSynchronization();
    try {
      mgr.doCreateNotification(
          NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
          "originator",
          null,
          Set.of(first),
          null,
          "test");
      mgr.doCreateNotification(
          NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
          "originator",
          null,
          Set.of(second),
          null,
          "test");
      Runnable laterCallback = Mockito.mock(Runnable.class);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              laterCallback.run();
            }
          });
      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertDoesNotThrow(() -> callbacks.forEach(TransactionSynchronization::afterCommit));
      Mockito.verify(notificnTracker).refreshNotificationCount(Mockito.eq(2L), Mockito.any());
      Mockito.verify(laterCallback).run();
      callbacks.forEach(c -> c.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

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
