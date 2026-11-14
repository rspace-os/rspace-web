package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class NotificationAfterCommitIT extends RealTransactionSpringTestBase {
  @Autowired private IMessageAndNotificationTracker tracker;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  void deliversCommittedNotificationInNewTransaction() throws Exception {
    checkDelivery(false, false);
  }

  @Test
  void suppressesBroadcastAndUnreadCountOnRollback() throws Exception {
    checkDelivery(true, false);
  }

  @Test
  void persistsFailureNotificationWhenBroadcasterThrowsAfterCommit() throws Exception {
    checkDelivery(false, true);
  }

  @Test
  void readDuringDeliveryDoesNotResurrectUnreadCount() throws Exception {
    checkDelivery(false, false, InterveningAction.READ);
  }

  @Test
  void readAllDuringDeliveryDoesNotResurrectUnreadCount() throws Exception {
    checkDelivery(false, false, InterveningAction.READ_ALL);
  }

  @Test
  void loginDuringDeliveryDoesNotDoubleCountCommittedNotification() throws Exception {
    checkDelivery(false, false, InterveningAction.LOGIN);
  }

  @Test
  void legacyCreationReconcilesAfterAnotherTransactionCommits() throws Exception {
    setUpLoginAsPIUserAndCreateADocument();
    User recipient = createAndSaveUser("legacy" + System.nanoTime());
    tracker.clearUserNotificationCount(recipient.getId());
    CommunicationManagerImpl manager = AopTestUtils.getUltimateTargetObject(communicationMgr);
    Object previous = ReflectionTestUtils.getField(manager, "broadcasters");
    Broadcaster broadcaster = mock(Broadcaster.class);
    manager.setBroadcasters(List.of(broadcaster));
    try {
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              status -> {
                communicationMgr.doCreateNotification(
                    NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
                    piUser.getUsername(),
                    null,
                    Set.of(recipient),
                    null,
                    "first transaction");
                assertEquals(0, tracker.getNotificationCountFor(recipient.getId()));
                verifyNoInteractions(broadcaster);
                TransactionTemplate second = new TransactionTemplate(getTxMger());
                second.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                second.executeWithoutResult(
                    inner ->
                        communicationMgr.doCreateNotification(
                            NotificationType.NOTIFICATION_REQUEST_STATUS_CHANGE,
                            piUser.getUsername(),
                            null,
                            Set.of(recipient),
                            null,
                            "second transaction"));
                assertEquals(1, tracker.getNotificationCountFor(recipient.getId()));
              });
      assertEquals(2, tracker.getNotificationCountFor(recipient.getId()));
      assertEquals(2, getNewNotificationCount(recipient));
    } finally {
      ReflectionTestUtils.setField(manager, "broadcasters", previous);
    }
  }

  private enum InterveningAction {
    NONE,
    READ,
    READ_ALL,
    LOGIN
  }

  private void checkDelivery(boolean rollback, boolean failBroadcast) throws Exception {
    checkDelivery(rollback, failBroadcast, InterveningAction.NONE);
  }

  private void checkDelivery(
      boolean rollback, boolean failBroadcast, InterveningAction interveningAction)
      throws Exception {
    var record = setUpLoginAsPIUserAndCreateADocument();
    User recipient = createAndSaveUser("notify" + System.nanoTime());
    userMgr.setPreference(
        Preference.NOTIFICATION_DOCUMENT_SHARED_PREF, "true", recipient.getUsername());
    tracker.clearUserNotificationCount(recipient.getId());
    CommunicationManagerImpl manager = AopTestUtils.getUltimateTargetObject(communicationMgr);
    Object previous = ReflectionTestUtils.getField(manager, "broadcasters");
    Broadcaster broadcaster = mock(Broadcaster.class);
    manager.setBroadcasters(List.of(broadcaster));
    if (failBroadcast)
      doThrow(new IllegalStateException("test broadcast failure"))
          .when(broadcaster)
          .broadcast(any(Communication.class));
    else if (!rollback)
      doAnswer(
              invocation -> {
                // Force entity access while the original transaction is suspended.
                Communication notification = invocation.getArgument(0);
                assertFalse(sessionFactory.getCurrentSession().contains(notification));
                assertNotNull(notification.getOriginator().getUsername());
                assertEquals(1, notification.getRecipients().size());
                if (interveningAction != InterveningAction.NONE) {
                  // Complete a separate request while post-commit delivery is paused here.
                  TransactionTemplate interveningRequest = new TransactionTemplate(getTxMger());
                  interveningRequest.setPropagationBehavior(
                      TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                  interveningRequest.executeWithoutResult(
                      status -> {
                        switch (interveningAction) {
                          case READ ->
                              communicationMgr.markNotificationsAsRead(
                                  Set.of(notification.getId()), recipient.getUsername());
                          case READ_ALL ->
                              communicationMgr.markAllNotificationsAsRead(
                                  recipient.getUsername(),
                                  new Date(System.currentTimeMillis() + 1000));
                          case LOGIN ->
                              tracker.initCount(
                                  recipient.getId(),
                                  () -> communicationMgr.getNotificationStatus(recipient));
                          default -> throw new IllegalStateException("Unexpected action");
                        }
                      });
                }
                return null;
              })
          .when(broadcaster)
          .broadcast(any(Communication.class));
    int originatorCount = tracker.getNotificationCountFor(piUser.getId());
    try {
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              status -> {
                communicationMgr.notify(
                    piUser,
                    record,
                    NotificationConfig.builder()
                        .notificationType(NotificationType.NOTIFICATION_DOCUMENT_SHARED)
                        .policyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY)
                        .notificationTargetsOverride(new HashSet<>(Set.of(recipient)))
                        .broadcast(true)
                        .build(),
                    "after commit test");
                verifyNoInteractions(broadcaster);
                assertEquals(0, tracker.getNotificationCountFor(recipient.getId()));
                if (rollback) status.setRollbackOnly();
              });
      if (rollback) verifyNoInteractions(broadcaster);
      else verify(broadcaster).broadcast(any(Communication.class));
      int expectedCount =
          rollback
                  || interveningAction == InterveningAction.READ
                  || interveningAction == InterveningAction.READ_ALL
              ? 0
              : 1;
      assertEquals(expectedCount, tracker.getNotificationCountFor(recipient.getId()));
      assertEquals(expectedCount, getNewNotificationCount(recipient));
      if (failBroadcast) {
        assertEquals(originatorCount + 1, tracker.getNotificationCountFor(piUser.getId()));
        assertEquals(1, getNewNotificationCount(piUser));
      }
    } finally {
      ReflectionTestUtils.setField(manager, "broadcasters", previous);
    }
  }
}
