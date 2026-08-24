package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.TimeSlotBooking;
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

    verify(auditTrail).notify(any(GenericEvent.class));
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

  @Test
  void writesCommittedBookingDefaultsEventToAuditTrail() throws Exception {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = mock(User.class);

    listener.bookingConfigurationDefaultsChanged(
        new BookingConfigurationDefaultsAuditEvent(
            actor, actor, new BookingConfigurationDefaults(), AuditAction.WRITE));

    verify(auditTrail).notify(any(GenericEvent.class));
    Method method =
        BookingAuditTrail.class.getDeclaredMethod(
            "bookingConfigurationDefaultsChanged", BookingConfigurationDefaultsAuditEvent.class);
    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        method.getAnnotation(TransactionalEventListener.class).phase());
  }

  @Test
  void writesCommittedTimeSlotBookingEventToAuditTrail() {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);

    listener.timeSlotBookingChanged(
        new TimeSlotBookingAuditEvent(
            mock(User.class), mock(User.class), new TimeSlotBooking(), AuditAction.WRITE));

    verify(auditTrail).notify(any(GenericEvent.class));
  }

  @Test
  void timeSlotBookingListenerRunsOnlyAfterCommit() throws Exception {
    Method listener =
        BookingAuditTrail.class.getDeclaredMethod(
            "timeSlotBookingChanged", TimeSlotBookingAuditEvent.class);

    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        listener.getAnnotation(TransactionalEventListener.class).phase());
  }
}
