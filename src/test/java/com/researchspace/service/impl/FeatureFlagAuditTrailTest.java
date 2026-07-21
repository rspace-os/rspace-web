package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.featureflags.FeatureFlagSource;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class FeatureFlagAuditTrailTest {

  @Test
  void writesCommittedFeatureFlagEventToAuditTrail() {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    FeatureFlagAuditTrail listener = new FeatureFlagAuditTrail(auditTrail);

    listener.featureFlagChanged(
        new FeatureFlagResourceChangedEvent(
            mock(User.class),
            new FeatureFlagResource(
                "bookingEnabled", true, false, true, FeatureFlagSource.USER_OVERRIDE, true)));

    verify(auditTrail).notify(org.mockito.ArgumentMatchers.any(GenericEvent.class));
  }

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
