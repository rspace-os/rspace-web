package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingNotificationRecipientReaderTest {

  private static final long INSTRUMENT_ID = 42L;

  private final BookingNotificationSubscriptionDao subscriptions =
      mock(BookingNotificationSubscriptionDao.class);
  private final UserDao users = mock(UserDao.class);
  private final BookingItemPermissions permissions = mock(BookingItemPermissions.class);
  private final BookingDisplayPreferencesManager displayPreferences =
      mock(BookingDisplayPreferencesManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final SessionFactory sessionFactory = mock(SessionFactory.class);
  private final Session session = mock(Session.class);

  private BookingNotificationRecipientReader reader;

  @BeforeEach
  void setUp() {
    when(sessionFactory.getCurrentSession()).thenReturn(session);
    when(displayPreferences.resolveForNotificationSnapshot(any(User.class)))
        .thenReturn(
            new BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences(
                "08:00",
                "18:00",
                BookingTimezoneMode.INSTITUTION,
                null,
                BookingTimeFormat.AUTOMATIC,
                "UTC",
                false));
    when(featureFlags.isFeatureFlagEnabledInSnapshot(eq(BOOKING_ENABLED), any(User.class)))
        .thenReturn(true);
    reader =
        new BookingNotificationRecipientReader(
            subscriptions,
            users,
            permissions,
            displayPreferences,
            featureFlags,
            sessionFactory,
            Clock.systemUTC());
  }

  @Test
  void addsSubscribedCancellationRequesterOnlyOnce() {
    User requester = user("cancel-requester", 3L);
    BookableItemNotificationSubscription subscription = subscriptionFor(requester);
    when(subscriptions.findEnabledByInstrument(INSTRUMENT_ID)).thenReturn(List.of(subscription));
    when(users.getSafeNull(requester.getId())).thenReturn(Optional.of(requester));
    when(permissions.resolve(any(BookableTargetReference.class), eq(requester)))
        .thenReturn(readableAccess());

    List<BookingNotificationRecipient> selected =
        reader.selectRecipients(
            INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CANCELLED, 99L, requester.getId());

    assertEquals(List.of(requester.getId()), selected.stream().map(r -> r.user().getId()).toList());
    assertTrue(selected.get(0).requester());
  }

  @Test
  void excludesCancellationRequesterWithoutReadResourceCapability() {
    User requester = user("revoked-requester", 4L);
    when(subscriptions.findEnabledByInstrument(INSTRUMENT_ID)).thenReturn(List.of());
    when(users.getSafeNull(requester.getId())).thenReturn(Optional.of(requester));
    when(permissions.resolve(any(BookableTargetReference.class), eq(requester)))
        .thenReturn(ResolvedResourceAccess.none());

    assertEquals(
        List.of(),
        reader.selectRecipients(
            INSTRUMENT_ID,
            NotificationType.NOTIFICATION_BOOKING_CANCELLED,
            99L,
            requester.getId()));
  }

  @ParameterizedTest
  @CsvSource({
    "owner,NOTIFICATION_BOOKING_CREATED,true,true,true,1",
    "owner,NOTIFICATION_BOOKING_CANCELLED,true,true,true,1",
    "subscriber,NOTIFICATION_BOOKING_CREATED,true,true,true,1",
    "owner,NOTIFICATION_BOOKING_CREATED,false,true,true,0",
    "owner,NOTIFICATION_BOOKING_CANCELLED,false,true,true,0",
    "owner,NOTIFICATION_BOOKING_CREATED,true,false,true,0",
    "owner,NOTIFICATION_BOOKING_CANCELLED,true,false,true,0",
    "owner,NOTIFICATION_BOOKING_CREATED,true,true,false,0"
  })
  void ownActionsRespectSubscriptionAndEventPreferences(
      String name,
      NotificationType event,
      boolean ownActions,
      boolean wantsEvent,
      boolean subscribed,
      int expected) {
    User actor = user(name, 7L);
    assertEquals(
        "true", actor.getValueForPreference(Preference.BOOKING_NOTIFY_OWN_ACTIONS).getValue());
    actor.setPreference(
        new UserPreference(
            Preference.BOOKING_NOTIFY_OWN_ACTIONS, actor, Boolean.toString(ownActions)));
    Preference eventPreference =
        event == NotificationType.NOTIFICATION_BOOKING_CREATED
            ? Preference.NOTIFICATION_BOOKING_CREATED_PREF
            : Preference.NOTIFICATION_BOOKING_CANCELLED_PREF;
    actor.setPreference(new UserPreference(eventPreference, actor, Boolean.toString(wantsEvent)));
    var candidates =
        subscribed
            ? List.of(subscriptionFor(actor))
            : List.<BookableItemNotificationSubscription>of();
    when(subscriptions.findEnabledByInstrument(INSTRUMENT_ID)).thenReturn(candidates);
    when(permissions.resolve(any(BookableTargetReference.class), eq(actor)))
        .thenReturn(readableAccess());
    assertEquals(
        expected,
        reader.selectRecipients(INSTRUMENT_ID, event, actor.getId(), actor.getId()).size());
  }

  private BookableItemNotificationSubscription subscriptionFor(User user) {
    BookableItemNotificationSubscription subscription =
        mock(BookableItemNotificationSubscription.class);
    when(subscription.getUser()).thenReturn(user);
    return subscription;
  }

  private User user(String username, long id) {
    User user = new User(username);
    user.setId(id);
    user.setFirstName("First");
    user.setLastName("Last");
    user.setEmail(username + "@example.org");
    return user;
  }

  private ResolvedResourceAccess readableAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }
}
