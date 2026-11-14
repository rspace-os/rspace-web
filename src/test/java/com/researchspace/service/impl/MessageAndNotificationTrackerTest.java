package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.webapp.messaging.NotificationMessage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
public class MessageAndNotificationTrackerTest {

  @Mock SimpMessagingTemplate messagingTemplate;

  @InjectMocks MessageAndNotificationTracker tracker;

  @Test
  void refreshWaitingForConnectionDoesNotBlockConnectionOwningPublisher() throws Exception {
    Semaphore pool = new Semaphore(1);
    CountDownLatch querying = new CountDownLatch(1);
    AtomicInteger acquisitions = new AtomicInteger();
    DataSource dataSource = Mockito.mock(DataSource.class);
    Mockito.when(dataSource.getConnection())
        .thenAnswer(
            invocation -> {
              if (acquisitions.incrementAndGet() > 1) {
                querying.countDown();
              }
              if (!pool.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new SQLException("Connection acquisition timed out");
              }
              Connection connection = Mockito.mock(Connection.class);
              Mockito.doAnswer(
                      close -> {
                        pool.release();
                        return null;
                      })
                  .when(connection)
                  .close();
              return connection;
            });
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    AtomicReference<Future<?>> refresh = new AtomicReference<>();
    var executor = Executors.newSingleThreadExecutor();
    try {
      transaction.executeWithoutResult(
          status -> {
            refresh.set(
                executor.submit(
                    () ->
                        tracker.refreshNotificationCount(
                            1L, () -> transaction.execute(query -> 1))));
            await(querying);
            // This transaction owns the sole connection needed by the pending refresh.
            tracker.updateMessageCount(1L, 1, MessageType.SIMPLE_MESSAGE);
          });
      refresh.get().get(5, TimeUnit.SECONDS);
      assertEquals(1, tracker.getMessageCountFor(1L));
      assertEquals(1, tracker.getNotificationCountFor(1L));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void olderRefreshRetriesAfterNewerPublication() throws Exception {
    CountDownLatch queried = new CountDownLatch(1);
    CountDownLatch publish = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var older =
          executor.submit(
              () ->
                  tracker.refreshNotificationCount(
                      1L,
                      () -> {
                        if (calls.incrementAndGet() == 1) {
                          queried.countDown();
                          await(publish);
                          return 1;
                        }
                        return 2;
                      }));
      assertTrue(queried.await(5, TimeUnit.SECONDS));
      tracker.refreshNotificationCount(1L, () -> 2);
      publish.countDown();
      older.get(5, TimeUnit.SECONDS);
      assertEquals(2, tracker.getNotificationCountFor(1L));
      assertEquals(2, calls.get());
    } finally {
      publish.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void refreshStopsAfterThreeConflictsAndPreservesDeltasAndClears() {
    AtomicInteger calls = new AtomicInteger();
    tracker.refreshNotificationCount(
        1L,
        () -> {
          int call = calls.incrementAndGet();
          if (call == 2) {
            tracker.clearUserNotificationCount(1L);
          } else {
            tracker.changeUserNotificationCount(1L, 1);
          }
          return 99;
        });
    assertEquals(3, calls.get());
    assertEquals(1, tracker.getNotificationCountFor(1L));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 1})
  void loginRetriesMessageBaselinesAfterOverlappingDeltas(int delta) throws Exception {
    CountDownLatch queried = new CountDownLatch(1);
    CountDownLatch publish = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var login =
          executor.submit(
              () ->
                  tracker.initCount(
                      1L,
                      () -> {
                        if (calls.incrementAndGet() == 1) {
                          queried.countDown();
                          await(publish);
                          return new NotificationStatus(0, 4, 6);
                        }
                        return new NotificationStatus(0, 4 + delta, 6 + delta);
                      }));
      assertTrue(queried.await(5, TimeUnit.SECONDS));
      tracker.updateMessageCount(1L, delta, MessageType.SIMPLE_MESSAGE);
      tracker.updateMessageCount(1L, delta, MessageType.REQUEST_CREATE_LAB_GROUP);
      publish.countDown();
      login.get(5, TimeUnit.SECONDS);
      assertEquals(2, calls.get());
      assertEquals(4 + delta, tracker.getMessageCountFor(1L));
      assertEquals(6 + delta, tracker.getSpecialMessageCountFor(1L));
      Mockito.verify(messagingTemplate)
          .convertAndSend(
              "/topic/notifications/1", new NotificationMessage(0, 4 + delta, 6 + delta));
    } finally {
      publish.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void loginPublishesLatestMessageBaselineAfterThreeConflicts() {
    AtomicInteger calls = new AtomicInteger();
    tracker.initCount(
        1L,
        () -> {
          int call = calls.incrementAndGet();
          tracker.updateMessageCount(1L, 1, MessageType.SIMPLE_MESSAGE);
          tracker.updateMessageCount(1L, -1, MessageType.REQUEST_CREATE_LAB_GROUP);
          return new NotificationStatus(0, 4 + call, 6 - call);
        });
    assertEquals(3, calls.get());
    assertEquals(7, tracker.getMessageCountFor(1L));
    assertEquals(3, tracker.getSpecialMessageCountFor(1L));
  }

  @Test
  void loginQueryDoesNotBlockOtherUsersOrOverwriteNewerCount() throws Exception {
    CountDownLatch queried = new CountDownLatch(1);
    CountDownLatch publish = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var login =
          executor.submit(
              () ->
                  tracker.initCount(
                      1L,
                      () -> {
                        queried.countDown();
                        await(publish);
                        return new NotificationStatus(1, 4, 5);
                      }));
      assertTrue(queried.await(5, TimeUnit.SECONDS));
      tracker.initCount(2L, () -> new NotificationStatus(3, 0, 0));
      tracker.refreshNotificationCount(1L, () -> 2);
      publish.countDown();
      login.get(5, TimeUnit.SECONDS);
      assertEquals(2, tracker.getNotificationCountFor(1L));
      assertEquals(3, tracker.getNotificationCountFor(2L));
      assertEquals(4, tracker.getMessageCountFor(1L));
      assertEquals(5, tracker.getSpecialMessageCountFor(1L));
    } finally {
      publish.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

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
  public void testNotificationServiceCalledOnNewNotification() {
    Long userId = 1L;
    tracker.changeUserNotificationCount(userId, 1);

    Mockito.verify(messagingTemplate)
        .convertAndSend("/topic/notifications/1", new NotificationMessage(1, 0, 0));
  }
}
