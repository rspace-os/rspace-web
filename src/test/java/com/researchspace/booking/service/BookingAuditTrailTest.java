package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailImpl;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.audittrail.HistoryDAO;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.testutils.TestFactory;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;
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
  void writesPermanentDeleteSnapshotOnlyAfterCommit() throws Exception {
    AuditTrailService auditTrail = mock(AuditTrailService.class);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = mock(User.class);
    BookingConfigurationPermanentDeleteSnapshot snapshot =
        new BookingConfigurationPermanentDeleteSnapshot(
            42L,
            3L,
            new BookableTargetReference(BookableTargetType.INSTRUMENT, 7L),
            "Microscope",
            BookingConfigurationState.ARCHIVED,
            2,
            1,
            4,
            Instant.parse("2026-09-01T12:00:00Z"));

    listener.bookingConfigurationPermanentlyDeleted(
        new BookingConfigurationPermanentDeleteAuditEvent(actor, actor, snapshot));

    verify(auditTrail).notify(any(GenericEvent.class));
    Method method =
        BookingAuditTrail.class.getDeclaredMethod(
            "bookingConfigurationPermanentlyDeleted",
            BookingConfigurationPermanentDeleteAuditEvent.class);
    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        method.getAnnotation(TransactionalEventListener.class).phase());
  }

  @Test
  void permanentDeleteSnapshotSerializesItsTimestampForTheAuditLog() {
    HistoryDAO historyDao = mock(HistoryDAO.class);
    AuditTrailImpl auditTrail = new AuditTrailImpl();
    auditTrail.setHistoryDao(historyDao);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfigurationPermanentDeleteSnapshot snapshot =
        new BookingConfigurationPermanentDeleteSnapshot(
            42L,
            3L,
            new BookableTargetReference(BookableTargetType.INSTRUMENT, 7L),
            "Microscope",
            BookingConfigurationState.ARCHIVED,
            2,
            1,
            4,
            Instant.parse("2026-09-01T12:00:00Z"));

    listener.bookingConfigurationPermanentlyDeleted(
        new BookingConfigurationPermanentDeleteAuditEvent(actor, actor, snapshot));

    ArgumentCaptor<Iterable<HistoricData>> records = ArgumentCaptor.forClass(Iterable.class);
    verify(historyDao).save(records.capture());
    String json = records.getValue().iterator().next().getData().toJson();
    assertTrue(json.contains("\"deletedAt\":\"2026-09-01T12:00:00Z\""), json);
    assertTrue(json.contains("\"target\":{\"type\":\"INSTRUMENT\",\"id\":7}"), json);
  }

  @Test
  void bookingConfigurationAuditSerializesItsTargetIdentity() {
    HistoryDAO historyDao = mock(HistoryDAO.class);
    AuditTrailImpl auditTrail = new AuditTrailImpl();
    auditTrail.setHistoryDao(historyDao);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(42L);
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 7L));
    configuration.setOpenDays(java.util.List.of(1, 6));
    configuration.setOpeningExceptions(
        java.util.List.of(
            new com.researchspace.model.booking.BookingOpeningException(6, "10:00", "16:00")));

    listener.bookingConfigurationChanged(
        new BookingConfigurationAuditEvent(actor, configuration, AuditAction.CREATE));

    ArgumentCaptor<Iterable<HistoricData>> records = ArgumentCaptor.forClass(Iterable.class);
    verify(historyDao).save(records.capture());
    String json = records.getValue().iterator().next().getData().toJson();
    AuditData parsed = AuditData.fromJson(json);
    assertTrue(json.contains("\"target\":{\"type\":\"INSTRUMENT\",\"id\":7}"), json);
    assertTrue(json.contains("\"openDays\":[1,6]"), json);
    assertTrue(
        json.contains(
            "\"openingExceptions\":[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}]"),
        json);
    assertEquals("booking-configurations:42", parsed.getData().get("id"));
    assertTrue(parsed.getData().get("target").toString().contains("INSTRUMENT"));
    assertEquals(AuditDomain.BOOKING, records.getValue().iterator().next().getDomain());
  }

  @Test
  void allBookingAuditPayloadTypesUseTheBookingDomain() {
    assertEquals(
        AuditDomain.BOOKING,
        BookingConfiguration.class.getAnnotation(AuditTrailData.class).auditDomain());
    assertEquals(
        AuditDomain.BOOKING,
        TimeSlotBooking.class.getAnnotation(AuditTrailData.class).auditDomain());
    assertEquals(
        AuditDomain.BOOKING,
        BookingConfigurationDefaults.class.getAnnotation(AuditTrailData.class).auditDomain());
    assertEquals(
        AuditDomain.BOOKING,
        BookingConfigurationPermanentDeleteSnapshot.class
            .getAnnotation(AuditTrailData.class)
            .auditDomain());
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
    HistoryDAO historyDao = mock(HistoryDAO.class);
    AuditTrailImpl auditTrail = new AuditTrailImpl();
    auditTrail.setHistoryDao(historyDao);
    BookingAuditTrail listener = new BookingAuditTrail(auditTrail);
    User actor = TestFactory.createAnyUser("sysadmin");
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(42L);
    booking.setBookingConfiguration(configuration);
    booking.setState(com.researchspace.model.booking.BookingState.CANCELLED);
    booking.setStartTime(java.util.Date.from(Instant.parse("2026-10-05T09:00:00Z")));
    booking.setEndTime(java.util.Date.from(Instant.parse("2026-10-05T10:00:00Z")));

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

    listener.timeSlotBookingChanged(
        new TimeSlotBookingAuditEvent(actor, actor, (TimeSlotBooking) proxy, AuditAction.WRITE));

    ArgumentCaptor<Iterable<HistoricData>> records = ArgumentCaptor.forClass(Iterable.class);
    verify(historyDao).save(records.capture());
    HistoricData record = records.getValue().iterator().next();
    AuditData parsed = AuditData.fromJson(record.getData().toJson());
    assertEquals(AuditDomain.BOOKING, record.getDomain());
    assertEquals(AuditAction.WRITE, record.getAction());
    assertEquals("bookings:42", parsed.getData().get("id"));
    assertEquals("booking-configurations:7", parsed.getData().get("bookingConfigurationId"));
    assertEquals("CANCELLED", parsed.getData().get("state"));
    assertEquals("BOOKING", parsed.getData().get("kind"));
    assertTrue(parsed.getData().containsKey("start"));
    assertTrue(parsed.getData().containsKey("end"));
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
