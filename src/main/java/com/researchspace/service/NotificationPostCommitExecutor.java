package com.researchspace.service;

/** Delivers notification effects independently of the transaction that created the notification. */
public interface NotificationPostCommitExecutor {
  /** Runs the action in a new transaction, suspending the triggering transaction's resources. */
  void execute(Runnable action);
}
