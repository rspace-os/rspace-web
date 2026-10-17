package com.researchspace.booking.service;

import static com.researchspace.booking.service.BookingCalendarManagerTest.BOOKING_ID;
import static com.researchspace.booking.service.BookingCalendarManagerTest.CONFIGURATION_ID;
import static com.researchspace.booking.service.BookingCalendarManagerTest.RAW_TOKEN;
import static com.researchspace.booking.service.BookingCalendarManagerTest.SERVER_BASE_URI;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createConfiguration;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createManager;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createOwner;
import static com.researchspace.booking.service.BookingCalendarManagerTest.ownerAccess;
import static com.researchspace.booking.service.BookingCalendarManagerTest.subscription;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingCalendarManagerFeedTest {

  private BookingCalendarSubscriptionDao subscriptionDao;
  private BookingConfigurationDao configurationDao;
  private UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private TimeSlotBookingManager bookingManager;
  private FeatureFlagManager featureFlags;
  private BookingItemPermissions accessManager;
  private BookingCalendarFeedGenerator generator;
  private BookingCalendarCreationTransaction creationTransaction;
  private BookingCalendarPermissionGateDao permissionGate;
  private BookingInstrumentQueryDao instrumentDao;
  private BookingCalendarManager manager;
  private User owner;
  private BookingConfiguration configuration;

  @BeforeEach
  void setUp() {
    subscriptionDao = mock(BookingCalendarSubscriptionDao.class);
    configurationDao = mock(BookingConfigurationDao.class);
    userSubscriptionDao = mock(UserBookingCalendarSubscriptionDao.class);
    bookingManager = mock(TimeSlotBookingManager.class);
    instrumentDao = mock(BookingInstrumentQueryDao.class);
    featureFlags = mock(FeatureFlagManager.class);
    accessManager = mock(BookingItemPermissions.class);
    generator = mock(BookingCalendarFeedGenerator.class);
    creationTransaction = mock(BookingCalendarCreationTransaction.class);
    permissionGate = mock(BookingCalendarPermissionGateDao.class);

    owner = createOwner();
    configuration = createConfiguration(owner);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(true);
    when(accessManager.resolve(configuration, owner)).thenReturn(ownerAccess());
    manager =
        createManager(
            subscriptionDao,
            userSubscriptionDao,
            configurationDao,
            bookingManager,
            instrumentDao,
            featureFlags,
            accessManager,
            generator,
            creationTransaction,
            permissionGate);
  }

  @Test
  void userSubscriptionTokenGeneratesTheOwnersCrossItemSource() throws Exception {
    UserBookingCalendarSubscription subscription =
        new UserBookingCalendarSubscription(
            owner, CryptoUtils.hashToken(RAW_TOKEN), RAW_TOKEN, new Date());
    CalendarSource source =
        new CalendarSource("booking:calendar.feed.myBookings", "UTC", List.of(), true);
    Date refreshedAt = new Date(1234L);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.empty());
    when(userSubscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getUserCalendarSource(owner, refreshedAt, 100)).thenReturn(source);
    when(generator.generate(source, SERVER_BASE_URI, Locale.ENGLISH, 10_000))
        .thenReturn(new byte[] {4, 5});

    BookingCalendarManager.Available result =
        assertInstanceOf(
            BookingCalendarManager.Available.class,
            manager.feed(RAW_TOKEN, Locale.ENGLISH, refreshedAt));

    assertEquals(2, result.body().length);
  }

  @Test
  void validFeedUsesTheOwnersCurrentSourceAndRequestedLocale() throws Exception {
    BookableItemCalendarSubscription subscription = subscription(configuration, owner);
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    Date refreshedAt = new Date(1234L);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(CONFIGURATION_ID, owner, refreshedAt, 100))
        .thenReturn(Optional.of(source));
    when(generator.generate(source, SERVER_BASE_URI, Locale.FRENCH, 10_000))
        .thenReturn(new byte[] {1, 2, 3});

    BookingCalendarManager.Available result =
        assertInstanceOf(
            BookingCalendarManager.Available.class,
            manager.feed(RAW_TOKEN, Locale.FRENCH, refreshedAt));

    assertEquals(3, result.body().length);
  }

  @Test
  void unavailableOwnerOrCurrentSourceRemainsConcealedWithoutDeletingTheRow() throws Exception {
    BookableItemCalendarSubscription subscription = subscription(configuration, owner);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    owner.setEnabled(false);

    assertInstanceOf(
        BookingCalendarManager.NotFound.class, manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
    owner.setEnabled(true);
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.empty());
    assertInstanceOf(
        BookingCalendarManager.NotFound.class, manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(false);
    assertInstanceOf(
        BookingCalendarManager.NotFound.class, manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
    verify(subscriptionDao, never()).removeForUserAndConfiguration(any(), any());
  }

  @Test
  void concurrentGenerationBeyondTheConfiguredLimitIsAtCapacity() throws Exception {
    BookableItemCalendarSubscription subscription = subscription(configuration, owner);
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    CountDownLatch generationEntered = new CountDownLatch(1);
    CountDownLatch releaseGeneration = new CountDownLatch(1);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.of(source));
    when(generator.generate(source, SERVER_BASE_URI, Locale.ENGLISH, 10_000))
        .thenAnswer(
            invocation -> {
              generationEntered.countDown();
              assertTrue(releaseGeneration.await(5, TimeUnit.SECONDS));
              return new byte[] {1};
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<BookingCalendarManager.FeedResult> first =
        executor.submit(() -> manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
    try {
      assertTrue(generationEntered.await(5, TimeUnit.SECONDS));
      assertInstanceOf(
          BookingCalendarManager.AtCapacity.class,
          manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
      releaseGeneration.countDown();
      assertInstanceOf(BookingCalendarManager.Available.class, first.get(5, TimeUnit.SECONDS));
    } finally {
      releaseGeneration.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void generationAndSizeFailuresAreUnavailable() throws Exception {
    BookableItemCalendarSubscription subscription = subscription(configuration, owner);
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.of(source));
    when(generator.generate(eq(source), any(), any(), eq(10_000)))
        .thenThrow(new BookingCalendarFeedGenerator.CalendarGenerationException("failed"));

    assertInstanceOf(
        BookingCalendarManager.Oversized.class,
        manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date()));
  }

  @Test
  void unexpectedFeedFailureIsUnavailableRatherThanConcealedAsMissing() {
    when(subscriptionDao.findByTokenHash(any())).thenThrow(new IllegalStateException());

    BookingCalendarManager.FeedResult result = manager.feed(RAW_TOKEN, Locale.ENGLISH, new Date());

    assertInstanceOf(BookingCalendarManager.Oversized.class, result);
  }

  @Test
  void downloadNamesTheFileAfterTheItemGlobalIdAndLocalStartDate() {
    TimeSlotBooking booking = confirmedBooking();
    stubDownload(booking, "Confocal Microscope!", "Europe/Berlin");
    when(generator.generateDownload(
            any(CalendarSource.class), eq(SERVER_BASE_URI), eq(Locale.FRENCH), eq(10_000)))
        .thenReturn(new byte[] {1, 2, 3});

    BookingCalendarManager.Download download =
        manager.download(BOOKING_ID, owner, Locale.FRENCH).orElseThrow();

    // 23:30 UTC is already the next day in Berlin, so the name follows the item's own timezone.
    assertEquals("confocal-microscope-IN101-2026-09-13.ics", download.filename());
    assertEquals(3, download.body().length);
  }

  @Test
  void downloadCarriesOnlyTheSingleRequestedBooking() {
    TimeSlotBooking booking = confirmedBooking();
    stubDownload(booking, "Microscope", "UTC");
    ArgumentCaptor<CalendarSource> generated = ArgumentCaptor.forClass(CalendarSource.class);
    when(generator.generateDownload(any(CalendarSource.class), any(), any(), eq(10_000)))
        .thenReturn(new byte[] {1});

    manager.download(BOOKING_ID, owner, Locale.ENGLISH).orElseThrow();

    verify(generator).generateDownload(generated.capture(), any(), any(), eq(10_000));
    assertEquals(1, generated.getValue().events().size());
    assertEquals(BOOKING_ID, generated.getValue().events().get(0).id());
    assertNull(generated.getValue().events().get(0).itemName());
    assertEquals("Microscope", generated.getValue().itemName());
  }

  @Test
  void downloadIsUnavailableWithoutReadAccessOrForAnUnconfirmedBooking() {
    TimeSlotBooking booking = confirmedBooking();
    stubDownload(booking, "Microscope", "UTC");
    when(accessManager.resolve(configuration, owner)).thenReturn(ResolvedResourceAccess.none());

    assertTrue(manager.download(BOOKING_ID, owner, Locale.ENGLISH).isEmpty());

    when(accessManager.resolve(configuration, owner)).thenReturn(ownerAccess());
    booking.setState(BookingState.CANCELLED);
    assertTrue(manager.download(BOOKING_ID, owner, Locale.ENGLISH).isEmpty());

    booking.setState(BookingState.CONFIRMED);
    when(bookingManager.getBooking(BOOKING_ID, owner)).thenReturn(Optional.empty());
    assertTrue(manager.download(BOOKING_ID, owner, Locale.ENGLISH).isEmpty());

    verify(generator, never()).generateDownload(any(), any(), any(), eq(10_000));
  }

  @Test
  void downloadRequiresAnActiveUserWithBookingEnabled() {
    TimeSlotBooking booking = confirmedBooking();
    stubDownload(booking, "Microscope", "UTC");

    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(false);
    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.download(BOOKING_ID, owner, Locale.ENGLISH));

    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(true);
    owner.setEnabled(false);
    assertThrows(
        AuthorizationException.class, () -> manager.download(BOOKING_ID, owner, Locale.ENGLISH));
  }

  private TimeSlotBooking confirmedBooking() {
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(BOOKING_ID);
    booking.setBookingConfiguration(configuration);
    booking.setRequester(owner);
    booking.setState(BookingState.CONFIRMED);
    booking.setStartTime(Date.from(Instant.parse("2026-09-12T23:30:00Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-09-13T01:30:00Z")));
    return booking;
  }

  private void stubDownload(TimeSlotBooking booking, String itemName, String timeZone) {
    configuration.setTimeZone(timeZone);
    when(bookingManager.getBooking(BOOKING_ID, owner)).thenReturn(Optional.of(booking));
    when(instrumentDao.getBookingSummaries(Set.of(101L)))
        .thenReturn(
            Map.of(101L, new InstrumentReadSummary(101L, itemName, false, null, null, null)));
  }
}
