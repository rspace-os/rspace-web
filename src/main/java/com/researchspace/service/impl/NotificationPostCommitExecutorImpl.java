package com.researchspace.service.impl;

import com.researchspace.service.NotificationPostCommitExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs notification side effects in a transaction independent of the triggering transaction. */
@Service("notificationPostCommitExecutor")
public class NotificationPostCommitExecutorImpl implements NotificationPostCommitExecutor {

  /** Runs the action after suspending any completed triggering transaction. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(Runnable action) {
    action.run();
  }
}
