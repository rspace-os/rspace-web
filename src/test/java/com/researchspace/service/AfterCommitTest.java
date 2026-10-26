package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void runsImmediatelyWithoutActiveSynchronization() {
    AtomicInteger calls = new AtomicInteger();

    AfterCommit.run(calls::incrementAndGet);

    assertEquals(1, calls.get());
  }

  @Test
  void runsAfterCommitWhenSynchronizationIsActive() {
    AtomicInteger calls = new AtomicInteger();
    TransactionSynchronizationManager.initSynchronization();

    AfterCommit.run(calls::incrementAndGet);
    assertEquals(0, calls.get());

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    assertEquals(1, calls.get());
  }

  @Test
  void doesNotRunAfterRollback() {
    AtomicInteger calls = new AtomicInteger();
    TransactionSynchronizationManager.initSynchronization();

    AfterCommit.run(calls::incrementAndGet);
    assertEquals(0, calls.get());

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertEquals(0, calls.get());
  }
}
