package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class BookingCalendarManagerTest {

  private static final long CONFIGURATION_ID = 11L;
  private static final String RAW_TOKEN = "A".repeat(43);

  private BookingCalendarSubscriptionDao subscriptionDao;
  private BookingConfigurationDao configurationDao;
  private UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private TimeSlotBookingManager bookingManager;
  private FeatureFlagManager featureFlags;
  private BookingCalendarFeedGenerator generator;
  private InstrumentDao instrumentDao;
  private BookingCalendarManager manager;
  private User owner;
  private BookingConfiguration configuration;

  @BeforeEach
  void setUp() {
    subscriptionDao = mock(BookingCalendarSubscriptionDao.class);
    configurationDao = mock(BookingConfigurationDao.class);
    userSubscriptionDao = mock(UserBookingCalendarSubscriptionDao.class);
    bookingManager = mock(TimeSlotBookingManager.class);
    instrumentDao = mock(InstrumentDao.class);
    featureFlags = mock(FeatureFlagManager.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceRegistry> resourceRegistry = mock(ObjectProvider.class);
    generator = mock(BookingCalendarFeedGenerator.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);

    owner = TestFactory.createAnyUser("owner");
    owner.setId(7L);
    configuration = new BookingConfiguration();
    configuration.setId(CONFIGURATION_ID);
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 101L));
    Instrument instrument = mock(Instrument.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example/context");
    when(resourceRegistry.getObject()).thenReturn(mock(ResourceRegistry.class));
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(true);
    when(configurationDao.lockById(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(configurationDao.getResources(any(), eq(1), any(RelationshipReadAccess.class)))
        .thenReturn(List.of(configuration));
    when(instrumentDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(instrument), 1));
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.empty());
    when(subscriptionDao.saveAndFlush(any(BookableItemCalendarSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userSubscriptionDao.saveAndFlush(any(UserBookingCalendarSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    manager =
        new BookingCalendarManagerImpl(
            subscriptionDao,
            userSubscriptionDao,
            configurationDao,
            bookingManager,
            instrumentDao,
            featureFlags,
            resourceRegistry,
            generator,
            new BookingCalendarProperties(100, 10_000, 1),
            properties,
            () -> RAW_TOKEN);
  }

  @Test
  void createStoresTheTokenAndReturnsTheFixedFeedQueryUrl() {
    BookingCalendarManager.Created created = manager.createOrRotate(CONFIGURATION_ID, owner, owner);

    ArgumentCaptor<BookableItemCalendarSubscription> saved =
        ArgumentCaptor.forClass(BookableItemCalendarSubscription.class);
    org.mockito.Mockito.verify(subscriptionDao).saveAndFlush(saved.capture());
    String storedHash = saved.getValue().getTokenHash();
    assertEquals(RAW_TOKEN, saved.getValue().getRawToken());
    assertEquals(64, storedHash.length());
    assertFalse(storedHash.contains(RAW_TOKEN));
    assertTrue(
        MessageDigest.isEqual(
            CryptoUtils.hashToken(RAW_TOKEN).getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)));

    URI url = URI.create(created.subscriptionUrl());
    assertEquals("https", url.getScheme());
    assertEquals("rspace.example", url.getHost());
    assertEquals("/context/public/booking/calendars/feed.ics", url.getPath());
    assertTrue(url.getRawQuery().startsWith("token="));
    assertEquals(RAW_TOKEN.length(), url.getRawQuery().substring("token=".length()).length());
    assertTrue(created.status().active());
  }

  @Test
  void statusRequiresAnActivePersonalCaller() {
    User delegate = TestFactory.createAnyUser("delegate");
    delegate.setId(8L);

    assertThrows(
        AuthorizationException.class, () -> manager.status(CONFIGURATION_ID, owner, delegate));
    owner.setEnabled(false);
    assertThrows(
        AuthorizationException.class, () -> manager.status(CONFIGURATION_ID, owner, owner));
  }

  @Test
  void statusIsInactiveWithoutASubscriptionUrl() {
    BookingCalendarManager.Status status = manager.status(CONFIGURATION_ID, owner, owner);

    assertFalse(status.active());
    assertNull(status.updatedAt());
    assertNull(status.subscriptionUrl());
  }

  @Test
  void userSubscriptionCreatesOnePrivateFeedForAllBookings() {
    BookingCalendarManager.Created created = manager.createOrRotateUser(owner, owner);

    ArgumentCaptor<UserBookingCalendarSubscription> saved =
        ArgumentCaptor.forClass(UserBookingCalendarSubscription.class);
    verify(userSubscriptionDao).saveAndFlush(saved.capture());
    assertSame(owner, saved.getValue().getUser());
    assertEquals(RAW_TOKEN, saved.getValue().getRawToken());
    assertEquals(64, saved.getValue().getTokenHash().length());
    assertEquals(
        "https://rspace.example/context/public/booking/calendars/feed.ics?token=" + RAW_TOKEN,
        created.subscriptionUrl());
  }

  @Test
  void userSubscriptionTokenGeneratesTheOwnersCrossItemSource() {
    UserBookingCalendarSubscription subscription =
        new UserBookingCalendarSubscription(
            owner, CryptoUtils.hashToken(RAW_TOKEN), RAW_TOKEN, new java.util.Date());
    CalendarSource source =
        new CalendarSource("booking:calendar.feed.myBookings", "UTC", List.of(), true);
    java.util.Date refreshedAt = new java.util.Date(1234L);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.empty());
    when(userSubscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getUserCalendarSource(owner, refreshedAt, 100)).thenReturn(source);
    when(generator.generate(
            source, URI.create("https://rspace.example/context"), Locale.ENGLISH, 10_000))
        .thenReturn(new byte[] {4, 5});

    BookingCalendarManager.Available result =
        assertInstanceOf(
            BookingCalendarManager.Available.class,
            manager.feed(RAW_TOKEN, Locale.ENGLISH, refreshedAt));

    assertEquals(2, result.body().length);
  }

  @Test
  void statusReturnsTheExistingSubscriptionUrl() {
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(subscription()));

    BookingCalendarManager.Status status = manager.status(CONFIGURATION_ID, owner, owner);

    assertTrue(status.active());
    assertEquals(
        "https://rspace.example/context/public/booking/calendars/feed.ics?token=previous",
        status.subscriptionUrl());
  }

  @Test
  void statusAndCreateConcealAMissingOrUnreadableConfiguration() {
    when(configurationDao.getResources(any(), eq(1), any(RelationshipReadAccess.class)))
        .thenReturn(List.of());

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.status(CONFIGURATION_ID, owner, owner));
    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner));
  }

  @Test
  void statusAndCreateConcealAnUnreadableTarget() {
    when(instrumentDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(), 0));

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.status(CONFIGURATION_ID, owner, owner));
    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner));
  }

  @Test
  void replaceUpdatesTheExistingCredentialInsteadOfCreatingAnotherRow() {
    BookableItemCalendarSubscription existing = subscription();
    String previousHash = existing.getTokenHash();
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(existing));

    manager.createOrRotate(CONFIGURATION_ID, owner, owner);

    ArgumentCaptor<BookableItemCalendarSubscription> saved =
        ArgumentCaptor.forClass(BookableItemCalendarSubscription.class);
    verify(subscriptionDao).saveAndFlush(saved.capture());
    assertSame(existing, saved.getValue());
    assertFalse(previousHash.equals(existing.getTokenHash()));
    assertEquals(RAW_TOKEN, existing.getRawToken());
  }

  @Test
  void revokeRemovesOnlyTheCallersRowAndIsIdempotent() {
    manager.revoke(CONFIGURATION_ID, owner, owner);
    manager.revoke(CONFIGURATION_ID, owner, owner);

    verify(subscriptionDao, times(2))
        .removeForUserAndConfiguration(owner.getId(), CONFIGURATION_ID);
    verify(subscriptionDao, never()).deleteByConfigurationId(any());
  }

  @Test
  void validFeedUsesTheOwnersCurrentSourceAndRequestedLocale() {
    BookableItemCalendarSubscription subscription = subscription();
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    java.util.Date refreshedAt = new java.util.Date(1234L);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(CONFIGURATION_ID, owner, refreshedAt, 100))
        .thenReturn(Optional.of(source));
    when(generator.generate(
            source, URI.create("https://rspace.example/context"), Locale.FRENCH, 10_000))
        .thenReturn(new byte[] {1, 2, 3});

    BookingCalendarManager.Available result =
        assertInstanceOf(
            BookingCalendarManager.Available.class,
            manager.feed(RAW_TOKEN, Locale.FRENCH, refreshedAt));

    assertEquals(3, result.body().length);
  }

  @Test
  void unavailableOwnerOrCurrentSourceRemainsConcealedWithoutDeletingTheRow() {
    BookableItemCalendarSubscription subscription = subscription();
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    owner.setEnabled(false);

    assertInstanceOf(
        BookingCalendarManager.NotFound.class,
        manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
    owner.setEnabled(true);
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.empty());
    assertInstanceOf(
        BookingCalendarManager.NotFound.class,
        manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(false);
    assertInstanceOf(
        BookingCalendarManager.NotFound.class,
        manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
    verify(subscriptionDao, never()).removeForUserAndConfiguration(any(), any());
  }

  @Test
  void concurrentGenerationBeyondTheConfiguredLimitIsAtCapacity() throws Exception {
    BookableItemCalendarSubscription subscription = subscription();
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    CountDownLatch generationEntered = new CountDownLatch(1);
    CountDownLatch releaseGeneration = new CountDownLatch(1);
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.of(source));
    when(generator.generate(
            source, URI.create("https://rspace.example/context"), Locale.ENGLISH, 10_000))
        .thenAnswer(
            invocation -> {
              generationEntered.countDown();
              assertTrue(releaseGeneration.await(5, TimeUnit.SECONDS));
              return new byte[] {1};
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<BookingCalendarManager.FeedResult> first =
        executor.submit(() -> manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
    try {
      assertTrue(generationEntered.await(5, TimeUnit.SECONDS));
      assertInstanceOf(
          BookingCalendarManager.AtCapacity.class,
          manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
      releaseGeneration.countDown();
      assertInstanceOf(BookingCalendarManager.Available.class, first.get(5, TimeUnit.SECONDS));
    } finally {
      releaseGeneration.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void generationAndSizeFailuresAreUnavailable() {
    BookableItemCalendarSubscription subscription = subscription();
    CalendarSource source = new CalendarSource("Microscope", "UTC", List.of());
    when(subscriptionDao.findByTokenHash(any())).thenReturn(Optional.of(subscription));
    when(bookingManager.getCalendarSource(eq(CONFIGURATION_ID), eq(owner), any(), eq(100)))
        .thenReturn(Optional.of(source));
    when(generator.generate(eq(source), any(), any(), eq(10_000)))
        .thenThrow(new BookingCalendarFeedGenerator.CalendarGenerationException("failed"));

    assertInstanceOf(
        BookingCalendarManager.Oversized.class,
        manager.feed(RAW_TOKEN, Locale.ENGLISH, new java.util.Date()));
  }

  @Test
  void unexpectedFeedFailureIsUnavailableRatherThanConcealedAsMissing() {
    when(subscriptionDao.findByTokenHash(any())).thenThrow(new IllegalStateException());

    BookingCalendarManager.FeedResult result =
        manager.feed(RAW_TOKEN, java.util.Locale.ENGLISH, new java.util.Date());

    assertInstanceOf(BookingCalendarManager.Oversized.class, result);
  }

  private BookableItemCalendarSubscription subscription() {
    return new BookableItemCalendarSubscription(
        configuration,
        owner,
        CryptoUtils.hashToken("previous"),
        "previous",
        new java.util.Date(1000L));
  }
}
