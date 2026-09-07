package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class FeatureFlagAuditTrailTest {

  @Test
  void listensOnlyAfterTheFeatureFlagTransactionCommits() throws Exception {
    Method listener =
        FeatureFlagAuditTrail.class.getDeclaredMethod(
            "featureFlagChanged", FeatureFlagResourceChangedEvent.class);

    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        listener.getAnnotation(TransactionalEventListener.class).phase());
  }
}
