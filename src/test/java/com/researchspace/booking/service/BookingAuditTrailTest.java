package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.booking.BookingConfiguration;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class BookingAuditTrailTest {

  @Test
  void writesCommittedBookingConfigurationEventToAuditTrail() {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);

    listener.bookingConfigurationChanged(
        new BookingConfigurationAuditEvent(
            mock(User.class), new BookingConfiguration(), AuditAction.CREATE));

    verify(auditTrail).notify(org.mockito.ArgumentMatchers.any(GenericEvent.class));
  }

  @Test
  void listensOnlyAfterTheDomainTransactionCommits() throws Exception {
    Method listener =
        BookingAuditTrail.class.getDeclaredMethod(
            "bookingConfigurationChanged", BookingConfigurationAuditEvent.class);

    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        listener.getAnnotation(TransactionalEventListener.class).phase());
  }
}
