package com.researchspace.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs notification side effects in a transaction independent of the triggering transaction. */
@Service
public class NotificationPostCommitExecutor {

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(Runnable action) {
    action.run();
  }
}
