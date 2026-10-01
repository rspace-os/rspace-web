package com.researchspace.booking.service;

import static com.researchspace.booking.service.BookingCalendarManagerTest.CONFIGURATION_ID;
import static com.researchspace.booking.service.BookingCalendarManagerTest.FEED_URL;
import static com.researchspace.booking.service.BookingCalendarManagerTest.RAW_TOKEN;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createConfiguration;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createManager;
import static com.researchspace.booking.service.BookingCalendarManagerTest.createOwner;
import static com.researchspace.booking.service.BookingCalendarManagerTest.ownerAccess;
import static com.researchspace.booking.service.BookingCalendarManagerTest.subscription;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Function;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class BookingCalendarManagerSubscriptionTest {

  private BookingCalendarSubscriptionDao subscriptionDao;
  private BookingConfigurationDao configurationDao;
  private UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private TimeSlotBookingManager bookingManager;
  private FeatureFlagManager featureFlags;
  private BookingItemPermissions accessManager;
  private BookingCalendarFeedGenerator generator;
  private BookingCalendarCreationTransaction creationTransaction;
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

    owner = createOwner();
    configuration = createConfiguration(owner);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(true);
    when(configurationDao.lockActiveById(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(configurationDao.lockById(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(configurationDao.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(accessManager.resolve(configuration, owner)).thenReturn(ownerAccess());
    when(accessManager.resolveForMutation(configuration, owner)).thenReturn(ownerAccess());
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.empty());
    when(subscriptionDao.saveAndFlush(any(BookableItemCalendarSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userSubscriptionDao.saveAndFlush(any(UserBookingCalendarSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userSubscriptionDao.lockUser(owner.getId())).thenReturn(owner);
    when(creationTransaction.create(eq(owner.getId()), any()))
        .thenAnswer(
            invocation -> {
              Function<User, BookingCalendarManager.Created> create = invocation.getArgument(1);
              return create.apply(owner);
            });

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
            creationTransaction);
  }

  @Test
  void createStoresTheTokenAndReturnsTheFixedFeedQueryUrl() {
    BookingCalendarManager.Created created =
        manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\"");

    ArgumentCaptor<BookableItemCalendarSubscription> saved =
        ArgumentCaptor.forClass(BookableItemCalendarSubscription.class);
    verify(subscriptionDao).saveAndFlush(saved.capture());
    String storedHash = saved.getValue().getTokenHash();
    assertEquals(RAW_TOKEN, saved.getValue().getRawToken());
    assertEquals(64, storedHash.length());
    assertFalse(storedHash.contains(RAW_TOKEN));
    assertTrue(
        MessageDigest.isEqual(
            CryptoUtils.hashToken(RAW_TOKEN).getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)));

    InOrder lockOrder = org.mockito.Mockito.inOrder(configurationDao, accessManager);
    lockOrder.verify(configurationDao).lockById(CONFIGURATION_ID);
    lockOrder.verify(accessManager).resolveForMutation(configuration, owner);

    URI url = URI.create(created.subscriptionUrl());
    assertEquals("https", url.getScheme());
    assertEquals("rspace.example", url.getHost());
    assertEquals("/context/public/booking/calendars/feed.ics", url.getPath());
    assertTrue(url.getRawQuery().startsWith("token="));
    assertEquals(RAW_TOKEN.length(), url.getRawQuery().substring("token=".length()).length());
    assertTrue(created.status().active());
  }

  @Test
  void resetUsesFreshInheritedPermissionFacts() {
    when(subscriptionDao.deleteByConfigurationId(CONFIGURATION_ID)).thenReturn(3);

    assertEquals(3, manager.resetForConfiguration(CONFIGURATION_ID, owner, owner));

    verify(accessManager).resolveForMutation(configuration, owner);
    verify(subscriptionDao).deleteByConfigurationId(CONFIGURATION_ID);
  }

  @Test
  void duplicateItemCreationCannotReplaceTheFirstSuccessfulCredential() {
    BookingCalendarManager.Created created =
        manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\"");
    ArgumentCaptor<BookableItemCalendarSubscription> saved =
        ArgumentCaptor.forClass(BookableItemCalendarSubscription.class);
    verify(subscriptionDao).saveAndFlush(saved.capture());
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(saved.getValue()));

    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\""));
    assertEquals(
        created.subscriptionUrl(),
        manager.status(CONFIGURATION_ID, owner, owner).subscriptionUrl());
    verify(subscriptionDao).saveAndFlush(any(BookableItemCalendarSubscription.class));
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
  void archivedConfigurationStatusIsInactiveAndCannotCreateALink() {
    configuration.setState(BookingConfigurationState.ARCHIVED);

    BookingCalendarManager.Status status = manager.status(CONFIGURATION_ID, owner, owner);

    assertFalse(status.active());
    assertNull(status.subscriptionUrl());
    assertThrows(
        BookingConfigurationLifecycleException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\""));
  }

  @Test
  void userSubscriptionCreatesOnePrivateFeedForAllBookings() {
    BookingCalendarManager.Created created =
        manager.createOrRotateUser(owner, owner, "\"inactive\"");

    ArgumentCaptor<UserBookingCalendarSubscription> saved =
        ArgumentCaptor.forClass(UserBookingCalendarSubscription.class);
    verify(userSubscriptionDao).saveAndFlush(saved.capture());
    assertSame(owner, saved.getValue().getUser());
    assertEquals(RAW_TOKEN, saved.getValue().getRawToken());
    assertEquals(64, saved.getValue().getTokenHash().length());
    assertEquals(FEED_URL + "?token=" + RAW_TOKEN, created.subscriptionUrl());
  }

  @Test
  void statusReturnsTheExistingSubscriptionUrl() {
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(subscription(configuration, owner)));

    BookingCalendarManager.Status status = manager.status(CONFIGURATION_ID, owner, owner);

    assertTrue(status.active());
    assertEquals(FEED_URL + "?token=previous", status.subscriptionUrl());
  }

  @ParameterizedTest
  @EnumSource(ConcealedConfiguration.class)
  void statusAndCreateConcealMissingOrUnreadableConfigurations(ConcealedConfiguration scenario) {
    if (scenario == ConcealedConfiguration.MISSING) {
      when(configurationDao.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.empty());
      when(configurationDao.lockActiveById(CONFIGURATION_ID)).thenReturn(Optional.empty());
      when(configurationDao.lockById(CONFIGURATION_ID)).thenReturn(Optional.empty());
    } else {
      when(accessManager.resolve(configuration, owner)).thenReturn(ResolvedResourceAccess.none());
      when(accessManager.resolveForMutation(configuration, owner))
          .thenReturn(ResolvedResourceAccess.none());
    }

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.status(CONFIGURATION_ID, owner, owner));
    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\""));
  }

  @Test
  void createConcealsAnArchivedConfigurationWithoutBookingAccess() {
    configuration.setState(BookingConfigurationState.ARCHIVED);
    when(accessManager.resolve(configuration, owner)).thenReturn(ResolvedResourceAccess.none());
    when(accessManager.resolveForMutation(configuration, owner))
        .thenReturn(ResolvedResourceAccess.none());

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner, "\"inactive\""));
  }

  @Test
  void replaceUpdatesTheExistingCredentialInsteadOfCreatingAnotherRow() {
    BookableItemCalendarSubscription existing = subscription(configuration, owner);
    String previousHash = existing.getTokenHash();
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(existing));

    String etag = manager.status(CONFIGURATION_ID, owner, owner).etag();
    manager.createOrRotate(CONFIGURATION_ID, owner, owner, etag);
    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.createOrRotate(CONFIGURATION_ID, owner, owner, etag));

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
  void revokeConcealsAConfigurationTheCallerCannotRead() {
    when(accessManager.resolve(configuration, owner)).thenReturn(ResolvedResourceAccess.none());

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.revoke(CONFIGURATION_ID, owner, owner));

    verify(subscriptionDao, never()).removeForUserAndConfiguration(owner.getId(), CONFIGURATION_ID);
  }

  private enum ConcealedConfiguration {
    MISSING,
    UNREADABLE
  }
}
