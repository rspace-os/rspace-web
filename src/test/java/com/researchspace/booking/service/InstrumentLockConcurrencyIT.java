package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class InstrumentLockConcurrencyIT extends RealTransactionSpringTestBase {
  private final InstrumentDao instruments;

  @Autowired
  InstrumentLockConcurrencyIT(InstrumentDao instruments) {
    this.instruments = instruments;
  }

  @Test
  void sortedInstrumentLocksDoNotLockTheOwnerBeforeTheRemainingInstruments() throws Exception {
    var owner = createInitAndLoginAnyUser();
    long first = createBasicInstrumentForUser(owner, "First lock target").getId();
    long second = createBasicInstrumentForUser(owner, "Second lock target").getId();
    assertTrue(first < second);
    var firstLocked = new CountDownLatch(1);
    var secondLocked = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(2);
    try {
      var batch =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          status -> {
                            assertTrue(instruments.lockById(first).isPresent());
                            firstLocked.countDown();
                            await(secondLocked);
                            assertTrue(instruments.lockById(second).isPresent());
                          }));
      var single =
          workers.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          status -> {
                            await(firstLocked);
                            // Stop after the single-target writer owns its instrument row, before
                            // refresh.
                            sessionFactory
                                .getCurrentSession()
                                .createNativeQuery(
                                    "select id from InstrumentEntity where id = :id for update",
                                    Long.class)
                                .setParameter("id", second)
                                .getSingleResult();
                            secondLocked.countDown();
                            assertTrue(instruments.lockById(second).isPresent());
                          }));
      batch.get(30, TimeUnit.SECONDS);
      single.get(30, TimeUnit.SECONDS);
    } finally {
      firstLocked.countDown();
      secondLocked.countDown();
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(20, TimeUnit.SECONDS));
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new AssertionError(failure);
    }
  }
}
