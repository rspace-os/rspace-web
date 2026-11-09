package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingNotificationSubscriptionDao;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class BookingNotificationRecipientReaderTest {

  private static final long INSTRUMENT_ID = 42L;

  private final BookingNotificationSubscriptionDao subscriptions =
      mock(BookingNotificationSubscriptionDao.class);
  private final BookingItemPermissions permissions = mock(BookingItemPermissions.class);
  private final BookingDisplayPreferencesManager displayPreferences =
      mock(BookingDisplayPreferencesManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);

  private BookingNotificationRecipientReader reader;
  private final Map<Long, User> testUsers = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(subscriptions.findRecipientUsers(any()))
        .thenAnswer(
            call ->
                ((Set<Long>) call.getArgument(0))
                    .stream().map(testUsers::get).filter(Objects::nonNull).toList());
    when(subscriptions.findReadableUsers(eq(INSTRUMENT_ID), any()))
        .thenAnswer(
            call ->
                ((Set<Long>) call.getArgument(1))
                    .stream()
                        .filter(
                            id -> {
                              var access =
                                  permissions.resolve(
                                      new BookableTargetReference(
                                          BookableTargetType.INSTRUMENT, INSTRUMENT_ID),
                                      testUsers.get(id));
                              return access != null
                                  && access.hasCapability(BookingResourceRoleScheme.READ_RESOURCE);
                            })
                        .collect(Collectors.toSet()));
    when(featureFlags.enabledUsersInSnapshot(eq(BOOKING_ENABLED), any()))
        .thenAnswer(call -> call.getArgument(1));
    when(displayPreferences.resolveForNotificationSnapshots(any()))
        .thenAnswer(
            call -> {
              Map<Long, BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences> result =
                  new HashMap<>();
              for (User user : (Collection<User>) call.getArgument(0))
                result.put(user.getId(), displayPreferences.resolveForNotificationSnapshot(user));
              return result;
            });
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
    reader =
        new BookingNotificationRecipientReader(
            subscriptions, displayPreferences, featureFlags, Clock.systemUTC());
  }

  @Test
  void excludesSubscriberWithoutReadResourceCapability() {
    User subscriber = user("revoked-reader", 2L);
    Long subscription = subscriptionFor(subscriber);
    when(subscriptions.findEnabledUserIds(INSTRUMENT_ID)).thenReturn(List.of(subscription));
    when(permissions.resolve(any(BookableTargetReference.class), eq(subscriber)))
        .thenReturn(ResolvedResourceAccess.none());

    assertEquals(
        List.of(),
        reader
            .selectRecipients(INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CREATED, 99L)
            .stream()
            .map(recipient -> recipient.user().getId())
            .toList());
  }

  @ParameterizedTest
  @EnumSource(
      value = NotificationType.class,
      names = {"NOTIFICATION_BOOKING_CANCELLED", "NOTIFICATION_BOOKING_CHANGED"})
  void addsSubscribedAffectedRequesterOnlyOnce(NotificationType event) {
    User requester = user("cancel-requester", 3L);
    Long subscription = subscriptionFor(requester);
    when(subscriptions.findEnabledUserIds(INSTRUMENT_ID)).thenReturn(List.of(subscription));
    when(permissions.resolve(any(BookableTargetReference.class), eq(requester)))
        .thenReturn(readableAccess());

    List<BookingNotificationRecipient> selected =
        reader.selectRecipients(INSTRUMENT_ID, event, 99L, requester.getId());

    assertEquals(List.of(requester.getId()), selected.stream().map(r -> r.user().getId()).toList());
    assertTrue(selected.get(0).requester());
    verify(displayPreferences).resolveForNotificationSnapshot(requester);
  }

  @Test
  void notifiesAnAffectedRequesterWithoutSubscriptionOrEventPreference() {
    User requester = user("changed-requester", 3L);
    requester.setPreference(
        new UserPreference(Preference.NOTIFICATION_BOOKING_CREATED_PREF, requester, "false"));
    when(subscriptions.findEnabledUserIds(INSTRUMENT_ID)).thenReturn(List.of());
    when(permissions.resolve(any(BookableTargetReference.class), eq(requester)))
        .thenReturn(readableAccess());
    List<BookingNotificationRecipient> selected =
        reader.selectRecipients(
            INSTRUMENT_ID, NotificationType.NOTIFICATION_BOOKING_CHANGED, 99L, requester.getId());
    assertEquals(List.of(requester.getId()), selected.stream().map(r -> r.user().getId()).toList());
    assertTrue(selected.get(0).requester());
  }

  @Test
  void excludesCancellationRequesterWithoutReadResourceCapability() {
    User requester = user("revoked-requester", 4L);
    when(subscriptions.findEnabledUserIds(INSTRUMENT_ID)).thenReturn(List.of());
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
    "owner,NOTIFICATION_BOOKING_RESTORED,true,true,true,1",
    "owner,NOTIFICATION_BOOKING_RESTORED,false,true,true,0",
    "owner,NOTIFICATION_BOOKING_RESTORED,true,false,true,0",
    "owner,NOTIFICATION_BOOKING_CHANGED,true,true,true,1",
    "owner,NOTIFICATION_BOOKING_CHANGED,false,true,true,0",
    "owner,NOTIFICATION_BOOKING_CHANGED,true,false,true,0",
    "owner,NOTIFICATION_BOOKING_CHANGED,true,true,false,0",
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
        event != NotificationType.NOTIFICATION_BOOKING_CANCELLED
            ? Preference.NOTIFICATION_BOOKING_CREATED_PREF
            : Preference.NOTIFICATION_BOOKING_CANCELLED_PREF;
    actor.setPreference(new UserPreference(eventPreference, actor, Boolean.toString(wantsEvent)));
    var candidates = subscribed ? List.of(subscriptionFor(actor)) : List.<Long>of();
    when(subscriptions.findEnabledUserIds(INSTRUMENT_ID)).thenReturn(candidates);
    when(permissions.resolve(any(BookableTargetReference.class), eq(actor)))
        .thenReturn(readableAccess());
    assertEquals(
        expected,
        reader.selectRecipients(INSTRUMENT_ID, event, actor.getId(), actor.getId()).size());
  }

  private Long subscriptionFor(User user) {
    return user.getId();
  }

  private User user(String username, long id) {
    User user = new User(username);
    user.setId(id);
    user.setFirstName("First");
    user.setLastName("Last");
    user.setEmail(username + "@example.org");
    testUsers.put(id, user);
    return user;
  }

  private ResolvedResourceAccess readableAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }
}
