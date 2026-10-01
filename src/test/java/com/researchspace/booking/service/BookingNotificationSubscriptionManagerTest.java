package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.TestFactory;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BookingNotificationSubscriptionManagerTest {

  private static final long CONFIGURATION_ID = 70L;
  private static final long INSTRUMENT_ID = 80L;

  private final BookingNotificationSubscriptionDao subscriptions =
      mock(BookingNotificationSubscriptionDao.class);
  private final BookingConfigurationDao configurations = mock(BookingConfigurationDao.class);
  private final InstrumentDao instruments = mock(InstrumentDao.class);
  private final BookingItemPermissions itemPermissions = mock(BookingItemPermissions.class);
  private final UserManager userManager = mock(UserManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final BookingFreshSubscriptionAccessReader freshAccess =
      mock(BookingFreshSubscriptionAccessReader.class);

  private final User owner = TestFactory.createAnyUser("notify-owner");
  private final User actor = owner;
  private final Instrument instrument = new Instrument();
  private final BookingConfiguration configuration = new BookingConfiguration();
  private final BookingNotificationSubscriptionManager manager =
      new BookingNotificationSubscriptionManagerImpl(
          subscriptions,
          configurations,
          instruments,
          itemPermissions,
          userManager,
          featureFlags,
          freshAccess);

  @BeforeEach
  void setUp() {
    when(freshAccess.canReadAll(any(), any())).thenReturn(true);
    owner.setId(5L);
    instrument.setId(INSTRUMENT_ID);
    instrument.setOwner(owner);
    configuration.setId(CONFIGURATION_ID);
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, INSTRUMENT_ID));
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)).thenReturn(true);
    when(configurations.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(instruments.getSafeNull(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));
    when(instruments.lockById(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));
    when(itemPermissions.resolveAll(any(), eq(owner)))
        .thenReturn(Map.of(CONFIGURATION_ID, readableAccess()));
  }

  @Test
  void initializationSnapshotsDefaultOffButNeverOverwritesAnExplicitOptOut() {
    when(userManager.getPreferenceForUser(owner, Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS))
        .thenReturn(
            new UserPreference(
                Preference.BOOKING_AUTO_SUBSCRIBE_NOTIFICATIONS, owner, Boolean.FALSE.toString()));
    when(subscriptions.findByUserAndInstrument(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.empty());
    when(subscriptions.findByUserAndInstrumentForUpdate(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.empty());
    when(subscriptions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    manager.initializeForInstrument(instrument);

    var captor = org.mockito.ArgumentCaptor.forClass(BookableItemNotificationSubscription.class);
    verify(subscriptions).saveAndFlush(captor.capture());
    assertFalse(captor.getValue().isEnabled());

    BookableItemNotificationSubscription optOut =
        new BookableItemNotificationSubscription(owner, instrument, false, new java.util.Date());
    when(subscriptions.findByUserAndInstrument(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.of(optOut));
    when(subscriptions.findByUserAndInstrumentForUpdate(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.of(optOut));
    manager.initializeForInstrument(instrument);

    verify(subscriptions).saveAndFlush(any());
    assertFalse(optOut.isEnabled());
  }

  @Test
  void statusSeparatesSavedSubscriptionFromGlobalEventAndEmailSettings() {
    when(userManager.getPreferenceForUser(owner, Preference.NOTIFICATION_BOOKING_CREATED_PREF))
        .thenReturn(preference(Preference.NOTIFICATION_BOOKING_CREATED_PREF, true));
    when(userManager.getPreferenceForUser(owner, Preference.NOTIFICATION_BOOKING_CANCELLED_PREF))
        .thenReturn(preference(Preference.NOTIFICATION_BOOKING_CANCELLED_PREF, false));
    when(userManager.getPreferenceForUser(owner, Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL))
        .thenReturn(preference(Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL, false));
    var saved =
        new BookableItemNotificationSubscription(owner, instrument, true, new java.util.Date());
    when(subscriptions.findByUserAndInstruments(owner.getId(), List.of(INSTRUMENT_ID)))
        .thenReturn(List.of(saved));

    var status = manager.get(CONFIGURATION_ID, owner, actor);

    assertTrue(status.enabled());
    assertTrue(status.createdEnabled());
    assertFalse(status.cancelledEnabled());
    assertFalse(status.emailEnabled());
  }

  @Test
  void staleSingleItemUpdatesConflictWithoutChangingTheSavedChoice() {
    var saved =
        new BookableItemNotificationSubscription(owner, instrument, false, new java.util.Date());
    when(subscriptions.findByUserAndInstrument(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.of(saved));
    when(subscriptions.findByUserAndInstrumentForUpdate(owner.getId(), INSTRUMENT_ID))
        .thenReturn(Optional.of(saved));

    assertThrows(
        BookingNotificationSubscriptionConflictException.class,
        () -> manager.replace(CONFIGURATION_ID, true, 1, owner, actor));

    assertFalse(saved.isEnabled());
    verify(subscriptions, never()).saveAndFlush(any());
  }

  @Test
  void sysadminReadsOnlyTheirOwnAbsentSubscription() {
    User sysadmin = TestFactory.createAnyUser("notify-admin");
    sysadmin.setId(9L);
    sysadmin.addRole(com.researchspace.model.Role.SYSTEM_ROLE);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, sysadmin)).thenReturn(true);
    when(configurations.getSafeNull(CONFIGURATION_ID)).thenReturn(Optional.of(configuration));
    when(itemPermissions.resolveAll(any(), eq(sysadmin)))
        .thenReturn(Map.of(CONFIGURATION_ID, readableAccess()));

    var status = manager.get(CONFIGURATION_ID, sysadmin, sysadmin);
    assertFalse(status.enabled());
    assertEquals(-1, status.version());
    verify(subscriptions).findByUserAndInstruments(sysadmin.getId(), List.of(INSTRUMENT_ID));
    verify(subscriptions, never()).saveAndFlush(any());
  }

  @Test
  void readerCanSaveAnExplicitOffWithoutChangingTheOwnersChoice() {
    User reader = TestFactory.createAnyUser("reader");
    reader.setId(6L);
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, reader)).thenReturn(true);
    when(itemPermissions.resolveAll(any(), eq(reader)))
        .thenReturn(Map.of(CONFIGURATION_ID, readableAccess()));
    when(subscriptions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var status = manager.replace(CONFIGURATION_ID, false, -1, reader, reader);

    assertFalse(status.enabled());
    var captor = org.mockito.ArgumentCaptor.forClass(BookableItemNotificationSubscription.class);
    verify(subscriptions).saveAndFlush(captor.capture());
    assertEquals(reader, captor.getValue().getUser());
    InOrder order = inOrder(instruments, freshAccess, subscriptions);
    order.verify(instruments).lockById(INSTRUMENT_ID);
    order.verify(freshAccess).canReadAll(List.of(configuration.getTarget()), reader.getId());
    order.verify(subscriptions).findByUserAndInstrumentForUpdate(reader.getId(), INSTRUMENT_ID);
  }

  @Test
  void revokedCommittedAccessPreventsWritesEvenWhenOuterSessionAllowsRead() {
    when(freshAccess.canReadAll(any(), eq(owner.getId()))).thenReturn(false);
    assertThrows(
        NotFoundException.class, () -> manager.replace(CONFIGURATION_ID, true, -1, owner, actor));
    verify(subscriptions, never()).saveAndFlush(any());
  }

  @Test
  void bulkValidatesAllAccessBeforeWritingAnySubscription() {
    BookingConfiguration hidden = new BookingConfiguration();
    hidden.setId(71L);
    hidden.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 81L));
    when(configurations.getSafeNull(71L)).thenReturn(Optional.of(hidden));
    assertThrows(
        NotFoundException.class,
        () -> manager.replaceMany(List.of(CONFIGURATION_ID, 71L), true, owner, actor));
    verify(subscriptions, never()).saveAndFlush(any());
  }

  @Test
  void unsubscribeAllLocksDormantInstrumentRowsInStableOrder() {
    when(subscriptions.findInstrumentIdsByUser(owner.getId())).thenReturn(List.of(20L, 10L));
    when(instruments.lockById(10L)).thenReturn(Optional.empty());
    when(instruments.lockById(20L)).thenReturn(Optional.empty());
    when(subscriptions.unsubscribeEnabledForUser(eq(owner.getId()), any())).thenReturn(2);

    assertEquals(2, manager.unsubscribeAll(owner, actor));

    InOrder inOrder = inOrder(instruments, subscriptions);
    inOrder.verify(instruments).lockById(10L);
    inOrder.verify(instruments).lockById(20L);
    inOrder.verify(subscriptions).unsubscribeEnabledForUser(eq(owner.getId()), any());
  }

  private static ResolvedResourceAccess readableAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }

  private UserPreference preference(Preference preference, boolean enabled) {
    return new UserPreference(preference, owner, Boolean.toString(enabled));
  }
}
