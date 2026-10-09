package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.comms.NotificationType;
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
  void excludesSubscriberWithoutReadResourceCapability() {
    User subscriber = user("revoked-reader", 2L);
    BookableItemNotificationSubscription subscription = subscriptionFor(subscriber);
    when(subscriptions.findEnabledByInstrument(INSTRUMENT_ID)).thenReturn(List.of(subscription));
    when(permissions.resolve(any(), eq(subscriber))).thenReturn(ResolvedResourceAccess.none());

    assertEquals(
        List.of(),
        reader
            .selectRecipients(INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CREATED, 99L)
            .stream()
            .map(recipient -> recipient.user().getId())
            .toList());
  }

  @Test
  void addsCancellationRequesterOnlyOnceWhenAlreadySubscribed() {
    User requester = user("cancel-requester", 3L);
    BookableItemNotificationSubscription subscription = subscriptionFor(requester);
    when(subscriptions.findEnabledByInstrument(INSTRUMENT_ID)).thenReturn(List.of(subscription));
    when(users.getSafeNull(requester.getId())).thenReturn(Optional.of(requester));
    when(permissions.resolve(any(), eq(requester))).thenReturn(readableAccess());

    List<BookingNotificationRecipient> selected =
        reader.selectRecipients(
            INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CANCELLED, 99L, requester.getId());

    assertEquals(List.of(requester.getId()), selected.stream().map(r -> r.user().getId()).toList());
    verify(displayPreferences).resolveForNotificationSnapshot(requester);
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
