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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.CurrentItemCalendarSubscription;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
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
    when(configurationDao.lockActiveById(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(configurationDao.lockById(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(configurationDao.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(accessManager.resolve(configuration, owner)).thenReturn(ownerAccess());
    when(accessManager.resolveForMutation(configuration, owner)).thenReturn(ownerAccess());
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.empty());
    when(subscriptionDao.saveAndFlush(any(BookableItemCalendarSubscription.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(subscriptionDao.reconcileCreated(owner.getId(), CONFIGURATION_ID))
        .thenReturn(
            new CurrentItemCalendarSubscription(
                41L,
                owner.getId(),
                CONFIGURATION_ID,
                CryptoUtils.hashToken(RAW_TOKEN),
                RAW_TOKEN,
                new Date()));
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
            creationTransaction,
            permissionGate);
  }

  @Test
  void createStoresTheTokenAndReturnsTheFixedFeedQueryUrl() {
    BookingCalendarManager.Created created = manager.create(CONFIGURATION_ID, owner, owner);

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

    InOrder lockOrder = inOrder(configurationDao, accessManager);
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
  void activeGateUsesCurrentScalarFactsWhenManagedSubjectStillLooksDisabled() {
    owner.setEnabled(false);
    when(permissionGate.hasActiveGate()).thenReturn(true);

    BookingCalendarManager.Created created = manager.create(CONFIGURATION_ID, owner, owner);

    assertTrue(created.status().active());
    verify(creationTransaction, never()).create(any(), any());
    verify(accessManager).resolveForMutation(configuration, owner);
  }

  @Test
  void activeGateFailsClosedWhenCurrentScalarFactsDenyAStaleEnabledSubject() {
    when(permissionGate.hasActiveGate()).thenReturn(true);
    when(accessManager.resolveForMutation(configuration, owner))
        .thenReturn(ResolvedResourceAccess.none());

    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.create(CONFIGURATION_ID, owner, owner));

    verify(subscriptionDao, never()).saveAndFlush(any(BookableItemCalendarSubscription.class));
  }

  @Test
  void suspendedAncestorIsRejectedBeforeAnyCallerStateIsRead() {
    User subject = mock(User.class);
    User actor = mock(User.class);
    doThrow(new IllegalStateException("suspended ancestor"))
        .when(permissionGate)
        .assertNoSuspendedAncestor();

    assertThrows(
        IllegalStateException.class, () -> manager.create(CONFIGURATION_ID, subject, actor));
    assertThrows(IllegalStateException.class, () -> manager.createUser(subject, actor));
    assertThrows(
        IllegalStateException.class,
        () -> manager.rotate(CONFIGURATION_ID, subject, actor, "\"etag\""));
    assertThrows(IllegalStateException.class, () -> manager.rotateUser(subject, actor, "\"etag\""));

    verifyNoInteractions(subject, actor);
  }

  @Test
  void resetUsesFreshInheritedPermissionFacts() {
    when(subscriptionDao.deleteByConfigurationId(CONFIGURATION_ID)).thenReturn(3);

    assertEquals(3, manager.resetForConfiguration(CONFIGURATION_ID, owner, owner));

    verify(accessManager).resolveForMutation(configuration, owner);
    verify(subscriptionDao).deleteByConfigurationId(CONFIGURATION_ID);
  }

  @Test
  void repeatedItemCreationReturnsTheFirstLinkUnchanged() {
    BookingCalendarManager.Created created = manager.create(CONFIGURATION_ID, owner, owner);
    ArgumentCaptor<BookableItemCalendarSubscription> saved =
        ArgumentCaptor.forClass(BookableItemCalendarSubscription.class);
    verify(subscriptionDao).saveAndFlush(saved.capture());
    CurrentItemCalendarSubscription current =
        new CurrentItemCalendarSubscription(
            41L,
            owner.getId(),
            CONFIGURATION_ID,
            saved.getValue().getTokenHash(),
            saved.getValue().getRawToken(),
            saved.getValue().getUpdatedAt());
    when(subscriptionDao.lockCurrent(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(current));
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(saved.getValue()));

    BookingCalendarManager.Created repeated = manager.create(CONFIGURATION_ID, owner, owner);

    assertTrue(created.newlyIssued());
    assertFalse(repeated.newlyIssued());
    assertEquals(created.subscriptionUrl(), repeated.subscriptionUrl());
    assertEquals(created.status().etag(), repeated.status().etag());
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
        () -> manager.create(CONFIGURATION_ID, owner, owner));
  }

  @Test
  void userSubscriptionCreatesOnePrivateFeedForAllBookings() {
    BookingCalendarManager.Created created = manager.createUser(owner, owner);

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
        .thenReturn(Optional.of(BookingCalendarManagerTest.subscription(configuration, owner)));

    BookingCalendarManager.Status status = manager.status(CONFIGURATION_ID, owner, owner);

    assertTrue(status.active());
    assertEquals(FEED_URL + "?token=previous", status.subscriptionUrl());
  }

  @ParameterizedTest
  @EnumSource(ConcealedConfiguration.class)
  void statusAndCreateConcealUnavailableConfigurations(ConcealedConfiguration scenario) {
    if (scenario == ConcealedConfiguration.MISSING) {
      when(configurationDao.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.empty());
      when(configurationDao.lockActiveById(CONFIGURATION_ID)).thenReturn(Optional.empty());
      when(configurationDao.lockById(CONFIGURATION_ID)).thenReturn(Optional.empty());
    } else {
      if (scenario == ConcealedConfiguration.ARCHIVED_WITHOUT_ACCESS) {
        configuration.setState(BookingConfigurationState.ARCHIVED);
      }
      when(accessManager.resolve(configuration, owner)).thenReturn(ResolvedResourceAccess.none());
      when(accessManager.resolveForMutation(configuration, owner))
          .thenReturn(ResolvedResourceAccess.none());
    }

    if (scenario != ConcealedConfiguration.ARCHIVED_WITHOUT_ACCESS) {
      assertThrows(
          BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
          () -> manager.status(CONFIGURATION_ID, owner, owner));
    }
    assertThrows(
        BookingCalendarManagerImpl.BookingCalendarNotFoundException.class,
        () -> manager.create(CONFIGURATION_ID, owner, owner));
  }

  @Test
  void rotateUpdatesTheExistingCredentialInsteadOfCreatingAnotherRow() {
    BookableItemCalendarSubscription existing =
        BookingCalendarManagerTest.subscription(configuration, owner);
    String previousHash = existing.getTokenHash();
    when(subscriptionDao.findByUserIdAndConfigurationId(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(existing));
    CurrentItemCalendarSubscription before =
        new CurrentItemCalendarSubscription(
            41L,
            owner.getId(),
            CONFIGURATION_ID,
            previousHash,
            existing.getRawToken(),
            existing.getUpdatedAt());
    CurrentItemCalendarSubscription after =
        new CurrentItemCalendarSubscription(
            41L,
            owner.getId(),
            CONFIGURATION_ID,
            CryptoUtils.hashToken(RAW_TOKEN),
            RAW_TOKEN,
            new Date());
    when(subscriptionDao.lockCurrent(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(before), Optional.of(after));
    when(subscriptionDao.rotateCurrent(eq(before), eq(previousHash), any(), eq(RAW_TOKEN), any()))
        .thenReturn(after);

    String etag = manager.status(CONFIGURATION_ID, owner, owner).etag();
    manager.rotate(CONFIGURATION_ID, owner, owner, etag);
    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.rotate(CONFIGURATION_ID, owner, owner, etag));

    verify(subscriptionDao)
        .rotateCurrent(eq(before), eq(previousHash), any(), eq(RAW_TOKEN), any());
    verify(subscriptionDao, never()).saveAndFlush(any(BookableItemCalendarSubscription.class));
    assertFalse(previousHash.equals(after.tokenHash()));
    assertEquals(RAW_TOKEN, after.rawToken());
  }

  @Test
  void conditionalRotationConflictUsesTheStableCalendarDomainException() {
    CurrentItemCalendarSubscription before =
        new CurrentItemCalendarSubscription(
            41L,
            owner.getId(),
            CONFIGURATION_ID,
            CryptoUtils.hashToken("before"),
            "before",
            new Date());
    when(subscriptionDao.lockCurrent(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(before));
    when(subscriptionDao.rotateCurrent(eq(before), eq(before.tokenHash()), any(), any(), any()))
        .thenThrow(new jakarta.persistence.OptimisticLockException("lost race"));

    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.rotate(CONFIGURATION_ID, owner, owner, "\"" + before.tokenHash() + "\""));
  }

  @Test
  void rotateConflictsWhenThereIsNoLinkToReplace() {
    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.rotate(CONFIGURATION_ID, owner, owner, "\"inactive\""));
    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.rotateUser(owner, owner, "\"inactive\""));
    verify(subscriptionDao, never()).saveAndFlush(any(BookableItemCalendarSubscription.class));
    verify(userSubscriptionDao, never()).saveAndFlush(any(UserBookingCalendarSubscription.class));
  }

  @Test
  void repeatedUserCreationReturnsTheExistingLinkAndRotationReplacesIt() {
    UserBookingCalendarSubscription existing =
        new UserBookingCalendarSubscription(
            owner, CryptoUtils.hashToken("previous"), "previous", new java.util.Date(1000L));
    when(userSubscriptionDao.findByUserId(owner.getId())).thenReturn(Optional.of(existing));

    BookingCalendarManager.Created repeated = manager.createUser(owner, owner);

    assertFalse(repeated.newlyIssued());
    assertEquals(
        "https://rspace.example/context/public/booking/calendars/feed.ics?token=previous",
        repeated.subscriptionUrl());
    verify(userSubscriptionDao, never()).saveAndFlush(any(UserBookingCalendarSubscription.class));

    BookingCalendarManager.Created rotated =
        manager.rotateUser(owner, owner, repeated.status().etag());

    assertTrue(rotated.newlyIssued());
    assertEquals(
        "https://rspace.example/context/public/booking/calendars/feed.ics?token=" + RAW_TOKEN,
        rotated.subscriptionUrl());
    assertSame(existing, verifySavedUserSubscription());
  }

  @Test
  void itemLinksListOnlyActiveReadableInstrumentsByName() {
    BookingConfiguration archived = configuration(15L, 102L);
    archived.setState(BookingConfigurationState.ARCHIVED);
    BookingConfiguration unreadable = configuration(12L, 103L);
    BookingConfiguration deletedItem = configuration(13L, 104L);
    BookingConfiguration second = configuration(14L, 105L);
    when(accessManager.resolveAll(any(), eq(owner)))
        .thenReturn(
            java.util.Map.of(
                configuration.getId(), ownerAccess(),
                unreadable.getId(), ResolvedResourceAccess.none(),
                deletedItem.getId(), ownerAccess(),
                second.getId(), ownerAccess()));
    when(subscriptionDao.findByUserId(owner.getId()))
        .thenReturn(
            List.of(
                subscription(configuration, "first"),
                subscription(archived, "archived"),
                subscription(unreadable, "unreadable"),
                subscription(deletedItem, "deleted"),
                subscription(second, "second")));
    when(instrumentDao.getBookingSummaries(java.util.Set.of(101L, 104L, 105L)))
        .thenReturn(
            java.util.Map.of(
                101L, instrument(101L, "microscope", false),
                104L, instrument(104L, "Centrifuge", true),
                105L, instrument(105L, "Autoclave", false)));

    List<BookingCalendarManager.ItemLink> links = manager.itemLinks(owner, owner);

    assertEquals(
        List.of("Autoclave", "microscope"),
        links.stream().map(BookingCalendarManager.ItemLink::itemName).toList());
    assertEquals(105L, links.get(0).targetId());
    assertEquals(14L, links.get(0).configurationId());
    assertEquals(
        "https://rspace.example/context/public/booking/calendars/feed.ics?token=second",
        links.get(0).subscriptionUrl());
    verify(accessManager)
        .resolveAll(eq(List.of(configuration, unreadable, deletedItem, second)), eq(owner));
    verify(accessManager, never()).resolve(any(BookingConfiguration.class), eq(owner));
  }

  @Test
  void itemLinksRequireAnActivePersonalCaller() {
    User delegate = TestFactory.createAnyUser("delegate");
    delegate.setId(8L);

    assertThrows(AuthorizationException.class, () -> manager.itemLinks(owner, delegate));
    verify(subscriptionDao, never()).findByUserId(any());
  }

  @Test
  void translatedConditionalRotationConflictUsesTheStableCalendarDomainException() {
    CurrentItemCalendarSubscription before =
        new CurrentItemCalendarSubscription(
            41L,
            owner.getId(),
            CONFIGURATION_ID,
            CryptoUtils.hashToken("before"),
            "before",
            new Date());
    when(subscriptionDao.lockCurrent(owner.getId(), CONFIGURATION_ID))
        .thenReturn(Optional.of(before));
    when(subscriptionDao.rotateCurrent(eq(before), eq(before.tokenHash()), any(), any(), any()))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                BookableItemCalendarSubscription.class, before.id()));

    assertThrows(
        BookingCalendarManagerImpl.UserSubscriptionConflictException.class,
        () -> manager.rotate(CONFIGURATION_ID, owner, owner, "\"" + before.tokenHash() + "\""));
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
    UNREADABLE,
    ARCHIVED_WITHOUT_ACCESS
  }

  private UserBookingCalendarSubscription verifySavedUserSubscription() {
    ArgumentCaptor<UserBookingCalendarSubscription> saved =
        ArgumentCaptor.forClass(UserBookingCalendarSubscription.class);
    verify(userSubscriptionDao).saveAndFlush(saved.capture());
    return saved.getValue();
  }

  private BookingConfiguration configuration(long id, long instrumentId) {
    BookingConfiguration value = new BookingConfiguration();
    value.setId(id);
    value.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    return value;
  }

  private BookableItemCalendarSubscription subscription(
      BookingConfiguration forConfiguration, String rawToken) {
    return new BookableItemCalendarSubscription(
        forConfiguration,
        owner,
        CryptoUtils.hashToken(rawToken),
        rawToken,
        new java.util.Date(1000L));
  }

  private static InstrumentReadSummary instrument(long id, String name, boolean deleted) {
    return new InstrumentReadSummary(id, name, deleted, null, null, null);
  }
}
