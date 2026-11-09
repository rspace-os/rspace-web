package com.researchspace.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs work immediately without transaction synchronization, otherwise after commit. */
public final class AfterCommit {

  private AfterCommit() {}

  /** Runs {@code action} immediately or registers it for the active synchronization's commit. */
  public static void run(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
