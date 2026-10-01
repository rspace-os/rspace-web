package com.researchspace.booking.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** Checks a transferred owner's subscription when a booking configuration commits concurrently. */
class BookingNotificationSubscriptionLifecycleIT extends RealTransactionSpringTestBase {

  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private BookingConfigurationDao configurations;
  @Autowired private BookingNotificationSubscriptionDao subscriptions;
  @Autowired private InstrumentDao instruments;
  @Autowired private ApplicationEventPublisher events;

  @org.junit.jupiter.api.BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @org.junit.jupiter.api.AfterEach
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @org.junit.jupiter.api.Test
  void transferSnapshotFindsAConfigurationCommittedWhileWaitingForInstrumentLock()
      throws Exception {
    User outgoingOwner = createInitAndLoginAnyUser();
    User incomingOwner = createAndSaveUser(getRandomName(10));
    setUpUserWithoutCustomContent(incomingOwner);
    userMgr.setPreference(
        Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS,
        Boolean.TRUE.toString(),
        incomingOwner.getUsername());
    logoutAndLoginAs(outgoingOwner);

    ApiInstrument created =
        createBasicInstrumentForUser(
            outgoingOwner, "Notification transfer race " + getRandomName(6));
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId());
    Instrument instrument;
    openTransaction();
    instrument = instruments.get(created.getId());
    Hibernate.initialize(instrument.getOwner());
    commitTransaction();
    ResolvedBookableTarget resolvedTarget = new ResolvedBookableTarget(target, instrument);

    CountDownLatch configurationInserted = new CountDownLatch(1);
    CountDownLatch transferAttemptingLock = new CountDownLatch(1);
    CountDownLatch allowConfigurationCommit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> configurationCreation =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationManager.createConfiguration(
                                new BookingConfigurationManager.Create(true, "UTC", resolvedTarget),
                                outgoingOwner,
                                outgoingOwner);
                            configurationInserted.countDown();
                            await(allowConfigurationCommit);
                          }));

      assertTrue(configurationInserted.await(20, TimeUnit.SECONDS));
      Future<Boolean> transfer =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .execute(
                          ignored -> {
                            // This ordinary read establishes a REPEATABLE_READ snapshot before
                            // the uncommitted configuration becomes visible.
                            assertTrue(configurations.findByTarget(target).isEmpty());
                            transferAttemptingLock.countDown();
                            Instrument locked = instruments.lockById(created.getId()).orElseThrow();
                            User currentOwner = locked.getOwner();
                            User nextOwner = userMgr.getUserByUsername(incomingOwner.getUsername());
                            locked.setOwner(nextOwner);
                            instruments.save(locked);
                            events.publishEvent(
                                new InventoryTransferEvent(
                                    locked, currentOwner, currentOwner, nextOwner));
                            return subscriptions
                                .findByUserAndInstrument(nextOwner.getId(), created.getId())
                                .map(subscription -> subscription.isEnabled())
                                .orElse(false);
                          }));

      assertTrue(transferAttemptingLock.await(20, TimeUnit.SECONDS));
      // Give the transfer thread time to reach the row lock while configuration creation holds it.
      Thread.sleep(100);
      allowConfigurationCommit.countDown();
      configurationCreation.get(20, TimeUnit.SECONDS);
      assertTrue(transfer.get(20, TimeUnit.SECONDS));
    } finally {
      allowConfigurationCommit.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for concurrent test step");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
