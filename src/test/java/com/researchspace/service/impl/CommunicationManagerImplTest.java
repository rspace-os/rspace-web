package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.researchspace.testutils.TestFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  public void setUp() {
    setDependencies(mgr);
  }

  @AfterEach
  public void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
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
  public void notificationSideEffectsWaitForCommitAndRunOnce() {
    User originator = TestFactory.createAnyUser("originator");
    User recipientOne = TestFactory.createAnyUser("recipientOne");
    recipientOne.setId(1L);
    User recipientTwo = TestFactory.createAnyUser("recipientTwo");
    recipientTwo.setId(2L);
    when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);
    mgr.setBroadcasters(Collections.singletonList(broadcaster1));
    executePostCommitCallbacksImmediately();
    TransactionSynchronizationManager.initSynchronization();

    mgr.notify(originator, null, notificationConfig(true, recipientOne, recipientTwo), "message");

    verify(commDao).save(any(Notification.class));
    verifyNoInteractions(broadcaster1, notificnTracker, notificationPostCommitExecutor);
    TransactionSynchronization synchronization = registeredSynchronization();

    synchronization.afterCommit();

    verify(notificationPostCommitExecutor).execute(any(Runnable.class));
    verify(broadcaster1).broadcast(any(Communication.class));
    verify(notificnTracker).changeUserNotificationCount(recipientOne.getId(), 1);
    verify(notificnTracker).changeUserNotificationCount(recipientTwo.getId(), 1);
  }

  @Test
  public void notificationSideEffectsDoNotRunAfterRollback() {
    User originator = TestFactory.createAnyUser("originator");
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(1L);
    when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);
    mgr.setBroadcasters(Collections.singletonList(broadcaster1));
    TransactionSynchronizationManager.initSynchronization();

    mgr.notify(originator, null, notificationConfig(true, recipient), "message");

    verify(commDao).save(any(Notification.class));
    TransactionSynchronization synchronization = registeredSynchronization();
    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verifyNoInteractions(broadcaster1, notificnTracker, notificationPostCommitExecutor);
  }

  @Test
  public void notificationTrackingWaitsForCommitWhenBroadcastIsDisabled() {
    User originator = TestFactory.createAnyUser("originator");
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(1L);
    when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);
    mgr.setBroadcasters(Collections.singletonList(broadcaster1));
    executePostCommitCallbacksImmediately();
    TransactionSynchronizationManager.initSynchronization();

    mgr.notify(originator, null, notificationConfig(false, recipient), "message");

    verifyNoInteractions(broadcaster1, notificnTracker, notificationPostCommitExecutor);
    registeredSynchronization().afterCommit();

    verify(notificationPostCommitExecutor).execute(any(Runnable.class));
    verifyNoInteractions(broadcaster1);
    verify(notificnTracker).changeUserNotificationCount(recipient.getId(), 1);
  }

  @Test
  public void notificationSideEffectsRemainImmediateWithoutSynchronization() {
    User originator = TestFactory.createAnyUser("originator");
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(1L);
    when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);
    mgr.setBroadcasters(Collections.singletonList(broadcaster1));

    mgr.notify(originator, null, notificationConfig(true, recipient), "message");

    verify(broadcaster1).broadcast(any(Communication.class));
    verify(notificnTracker).changeUserNotificationCount(recipient.getId(), 1);
  }

  @Test
  public void throwingBroadcasterSavesAndDeliversProcessFailedNotification() throws Exception {
    CommunicationManagerImpl realManager = new CommunicationManagerImpl();
    setDependencies(realManager);
    realManager.setBroadcasters(Collections.singletonList(broadcaster1));

    User originator = TestFactory.createAnyUser("originator");
    originator.setId(1L);
    User recipient = TestFactory.createAnyUser("recipient");
    recipient.setId(2L);
    User sysadmin = TestFactory.createAnyUser("sysadmin1");
    when(userDao.getUserByUsername(originator.getUsername())).thenReturn(originator);
    when(userDao.getUserByUsername(sysadmin.getUsername())).thenReturn(sysadmin);
    Mockito.doThrow(RuntimeException.class)
        .when(broadcaster1)
        .broadcast(Mockito.any(Communication.class));
    javax.sql.DataSource dataSource = Mockito.mock(javax.sql.DataSource.class);
    java.util.List<java.sql.Connection> connections = new java.util.ArrayList<>();
    when(dataSource.getConnection())
        .thenAnswer(
            ignored -> {
              java.sql.Connection connection = Mockito.mock(java.sql.Connection.class);
              when(connection.getAutoCommit()).thenReturn(true);
              connections.add(connection);
              return connection;
            });
    org.springframework.jdbc.datasource.DataSourceTransactionManager transactions =
        new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
    org.springframework.aop.framework.ProxyFactory factory =
        new org.springframework.aop.framework.ProxyFactory(new NotificationPostCommitExecutor());
    factory.addAdvice(
        new org.springframework.transaction.interceptor.TransactionInterceptor(
            transactions,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
    ReflectionTestUtils.setField(realManager, "notificationPostCommitExecutor", factory.getProxy());
    java.util.List<Object> saveTransactions = new java.util.ArrayList<>();
    when(commDao.save(any(Notification.class)))
        .thenAnswer(
            invocation -> {
              saveTransactions.add(TransactionSynchronizationManager.getResource(dataSource));
              return invocation.getArgument(0);
            });

    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              realManager.notify(originator, null, notificationConfig(true, recipient), "message");
              verifyNoInteractions(broadcaster1, notificnTracker);
            });

    assertEquals(2, saveTransactions.size());
    org.junit.jupiter.api.Assertions.assertNotNull(saveTransactions.get(0));
    org.junit.jupiter.api.Assertions.assertNotNull(saveTransactions.get(1));
    org.junit.jupiter.api.Assertions.assertNotSame(
        saveTransactions.get(0), saveTransactions.get(1));
    assertEquals(3, connections.size());
    for (java.sql.Connection connection : connections) {
      verify(connection).commit();
    }

    org.mockito.ArgumentCaptor<Notification> notifications =
        org.mockito.ArgumentCaptor.forClass(Notification.class);
    Mockito.verify(commDao, Mockito.times(2)).save(notifications.capture());
    assertEquals(
        NotificationType.NOTIFICATION_DOCUMENT_SHARED,
        notifications.getAllValues().get(0).getNotificationType());
    assertEquals(
        NotificationType.PROCESS_FAILED, notifications.getAllValues().get(1).getNotificationType());
    Mockito.verify(broadcaster1, Mockito.times(1)).broadcast(Mockito.any(Communication.class));
    verify(notificnTracker).changeUserNotificationCount(recipient.getId(), 1);
    verify(notificnTracker).changeUserNotificationCount(originator.getId(), 1);
  }

  private void setDependencies(CommunicationManagerImpl manager) {
    ReflectionTestUtils.setField(manager, "commDao", commDao);
    ReflectionTestUtils.setField(manager, "userDao", userDao);
    ReflectionTestUtils.setField(manager, "notificnTracker", notificnTracker);
    ReflectionTestUtils.setField(
        manager, "notificationPostCommitExecutor", notificationPostCommitExecutor);
  }

  private void executePostCommitCallbacksImmediately() {
    Mockito.doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(notificationPostCommitExecutor)
        .execute(Mockito.any(Runnable.class));
  }

  private NotificationConfig notificationConfig(boolean broadcast, User... recipients) {
    return NotificationConfig.builder()
        .notificationType(NotificationType.NOTIFICATION_DOCUMENT_SHARED)
        .broadcast(broadcast)
        .policyOverride(CommunicationNotifyPolicy.ALWAYS_NOTIFY)
        .notificationTargetsOverride(new HashSet<>(Set.of(recipients)))
        .build();
  }

  private TransactionSynchronization registeredSynchronization() {
    assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    return TransactionSynchronizationManager.getSynchronizations().get(0);
  }
}
