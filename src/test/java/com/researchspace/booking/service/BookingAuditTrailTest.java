package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailImpl;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.audittrail.HistoryDAO;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.testutils.TestFactory;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyFactory;
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class BookingAuditTrailTest {

  @Test
  void keepsSelfActionsUndescribedAndIdentifiesDelegatedSubjects() {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = mock(User.class);
    User subject = mock(User.class);
    when(subject.getUsername()).thenReturn("delegated-user");

    listener.bookingConfigurationChanged(
        new BookingConfigurationAuditEvent(
            actor, actor, new BookingConfiguration(), AuditAction.WRITE));
    listener.timeSlotBookingChanged(
        new TimeSlotBookingAuditEvent(actor, subject, new TimeSlotBooking(), AuditAction.WRITE));

    ArgumentCaptor<GenericEvent> events = ArgumentCaptor.forClass(GenericEvent.class);
    verify(auditTrail, times(2)).notify(events.capture());
    assertNull(events.getAllValues().get(0).getDescription());
    assertEquals("subject=delegated-user", events.getAllValues().get(1).getDescription());
  }

  @Test
  void everyAuditListenerRunsOnlyAfterCommit() throws Exception {
    List<Method> listeners =
        List.of(
            BookingAuditTrail.class.getDeclaredMethod(
                "bookingConfigurationChanged", BookingConfigurationAuditEvent.class),
            BookingAuditTrail.class.getDeclaredMethod(
                "bookingConfigurationDefaultsChanged",
                BookingConfigurationDefaultsAuditEvent.class),
            BookingAuditTrail.class.getDeclaredMethod(
                "bookingConfigurationPermanentlyDeleted",
                BookingConfigurationPermanentDeleteAuditEvent.class),
            BookingAuditTrail.class.getDeclaredMethod(
                "timeSlotBookingChanged", TimeSlotBookingAuditEvent.class));

    for (Method listener : listeners) {
      assertEquals(
          TransactionPhase.AFTER_COMMIT,
          listener.getAnnotation(TransactionalEventListener.class).phase(),
          listener.getName());
    }
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
  }

  @Test
  void newConfigurationSnapshotsContainIndependentHours() {
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    configuration.setOpeningHours(openingHoursSnapshot());

    SavedRecord saved =
        savedRecord(
            listener ->
                listener.bookingConfigurationChanged(
                    new BookingConfigurationAuditEvent(
                        actor, actor, configuration, AuditAction.WRITE)));
    AuditData parsed = saved.data();
    String json = parsed.toJson();
    assertTrue(
        json.contains("\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\"}]"),
        json);
    assertFalse(parsed.getData().containsKey("openingStart"));
    assertFalse(parsed.getData().containsKey("openingEnd"));
  }

  @Test
  void writesPermanentDeleteSnapshotToAuditTrail() {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    User actor = mock(User.class);
    BookingConfigurationPermanentDeleteSnapshot snapshot =
        new BookingConfigurationPermanentDeleteSnapshot(
            42L,
            3L,
            null,
            "Microscope",
            BookingConfigurationState.ARCHIVED,
            2,
            1,
            4,
            Instant.parse("2026-09-01T12:00:00Z"));

    new BookingAuditTrail(auditTrail)
        .bookingConfigurationPermanentlyDeleted(
            new BookingConfigurationPermanentDeleteAuditEvent(actor, actor, snapshot));

    verify(auditTrail).notify(any(GenericEvent.class));
  }

  @Test
  void permanentDeleteSnapshotSerializesItsTimestampForTheAuditLog() {
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfigurationPermanentDeleteSnapshot snapshot =
        new BookingConfigurationPermanentDeleteSnapshot(
            42L,
            3L,
            null,
            "Microscope",
            BookingConfigurationState.ARCHIVED,
            2,
            1,
            4,
            Instant.parse("2026-09-01T12:00:00Z"));

    String json =
        savedRecord(
                listener ->
                    listener.bookingConfigurationPermanentlyDeleted(
                        new BookingConfigurationPermanentDeleteAuditEvent(actor, actor, snapshot)))
            .data()
            .toJson();
    assertTrue(json.contains("\"deletedAt\":\"2026-09-01T12:00:00Z\""), json);
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
  void initializedHibernateProxyRetainsBookingAuditDetails() throws Exception {
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(42L);
    booking.setBookingConfiguration(configuration);
    booking.setState(BookingState.CANCELLED);
    booking.setStartTime(Date.from(Instant.parse("2026-10-05T09:00:00Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-10-05T10:00:00Z")));

    // The archive DAO returns initialized getReference proxies, not entity instances.
    ByteBuddyProxyFactory factory =
        new ByteBuddyProxyFactory(new ByteBuddyProxyHelper(new ByteBuddyState()));
    factory.postInstantiate(
        TimeSlotBooking.class.getName(),
        TimeSlotBooking.class,
        Set.of(HibernateProxy.class),
        TimeSlotBooking.class.getMethod("getId"),
        TimeSlotBooking.class.getMethod("setId", Long.class),
        null);
    HibernateProxy proxy = factory.getProxy(42L, null);
    proxy.getHibernateLazyInitializer().setImplementation(booking);

    SavedRecord saved =
        savedRecord(
            listener ->
                listener.timeSlotBookingChanged(
                    new TimeSlotBookingAuditEvent(
                        actor, actor, (TimeSlotBooking) proxy, AuditAction.WRITE)));
    HistoricData record = saved.historicData();
    AuditData parsed = saved.data();
    assertEquals(AuditDomain.UNKNOWN, record.getDomain());
    assertEquals(AuditAction.WRITE, record.getAction());
    assertEquals("bookings:42", parsed.getData().get("id"));
    assertEquals("booking-configurations:7", parsed.getData().get("bookingConfigurationId"));
    assertEquals("CANCELLED", parsed.getData().get("state"));
    assertEquals("BOOKING", parsed.getData().get("kind"));
    assertTrue(parsed.getData().containsKey("start"));
    assertTrue(parsed.getData().containsKey("end"));
  }

  private record SavedRecord(HistoricData historicData, AuditData data) {}

  private static SavedRecord savedRecord(Consumer<BookingAuditTrail> event) {
    HistoryDAO historyDao = mock(HistoryDAO.class);
    AuditTrailImpl auditTrail = new AuditTrailImpl();
    auditTrail.setHistoryDao(historyDao);
    event.accept(new BookingAuditTrail(auditTrail));

    ArgumentCaptor<Iterable<HistoricData>> records = ArgumentCaptor.forClass(Iterable.class);
    verify(historyDao).save(records.capture());
    HistoricData record = records.getValue().iterator().next();
    return new SavedRecord(record, AuditData.fromJson(record.getData().toJson()));
  }

  private static List<BookingOpeningHoursEntry> openingHoursSnapshot() {
    return List.of(new BookingOpeningHoursEntry(1, "09:00", "17:00"));
  }
}
