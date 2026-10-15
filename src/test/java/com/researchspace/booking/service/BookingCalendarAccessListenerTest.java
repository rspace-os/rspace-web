package com.researchspace.booking.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarSubscriptionCandidate;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.CommunityPermissionsChangedEvent;
import com.researchspace.model.GroupPermissionsChangedEvent;
import com.researchspace.model.User;
import com.researchspace.model.UserSavedEvent;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

class BookingCalendarAccessListenerTest {
  private final BookingCalendarSubscriptionDao subscriptions =
      mock(BookingCalendarSubscriptionDao.class);
  private final BookingConfigurationDao configurations = mock(BookingConfigurationDao.class);
  private final UserDao users = mock(UserDao.class);
  private final BookingItemPermissions permissions = mock(BookingItemPermissions.class);
  private final BookingCalendarAccessListener listener =
      new BookingCalendarAccessListener(subscriptions, configurations, users, permissions);
  private final User user = TestFactory.createAnyUser("subscriber");
  private final BookingConfiguration configuration = new BookingConfiguration();
  private final BookingCalendarSubscriptionCandidate subscription =
      new BookingCalendarSubscriptionCandidate(13L, 7L, 11L);
  private final Instrument item = mock(Instrument.class);

  @BeforeEach
  void setup() {
    user.setId(7L);
    configuration.setId(11L);
    when(subscriptions.findCandidatesByUserId(7L)).thenReturn(List.of(subscription));
    when(subscriptions.findCandidatesByConfigurationId(11L)).thenReturn(List.of(subscription));
    when(subscriptions.findCandidatesForMembershipRevalidation()).thenReturn(List.of(subscription));
    when(configurations.getSafeNull(11L)).thenReturn(Optional.of(configuration));
    when(users.getSafeNull(7L)).thenReturn(Optional.of(user));
    when(item.isInstrument()).thenReturn(true);
    when(item.getId()).thenReturn(17L);
    when(configurations.lockByTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, 17L)))
        .thenReturn(Optional.of(configuration));
    when(permissions.resolveAll(List.of(configuration), user))
        .thenReturn(Map.of(11L, ResolvedResourceAccess.none()));
  }

  @ParameterizedTest
  @EnumSource(RevocationEvent.class)
  void permissionChangesRevokeTheLink(RevocationEvent event) {
    switch (event) {
      case ITEM_EDITED -> listener.itemEdited(new InventoryEditingEvent(item, user));
      case ITEM_TRANSFERRED ->
          listener.itemTransferred(
              new InventoryTransferEvent(item, user, user, TestFactory.createAnyUser("new-owner")));
      case USER_SAVED -> listener.userSaved(new UserSavedEvent(user));
      case GROUP_CHANGED -> listener.permissionsChanged(new GroupPermissionsChangedEvent(null));
      case COMMUNITY_CHANGED ->
          listener.permissionsChanged(new CommunityPermissionsChangedEvent(null));
    }

    verify(subscriptions).deleteById(13L);
  }

  @Test
  void itemChangesLockConfigurationBeforePermissionFence() {
    listener.itemEdited(new InventoryEditingEvent(item, user));

    InOrder order = inOrder(configurations, subscriptions);
    order
        .verify(configurations)
        .lockByTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 17L));
    order.verify(subscriptions).lockPermissionFence();
  }

  @Test
  void retainedReadAccessKeepsLink() {
    when(permissions.resolveAll(List.of(configuration), user))
        .thenReturn(Map.of(11L, BookingAccessTestFixtures.readableAccess()));
    listener.itemEdited(new InventoryEditingEvent(item, user));
    verify(subscriptions, never()).deleteById(13L);
  }

  @Test
  void missingUserRevokesLinkWithoutLoadingTheSubscription() {
    when(users.getSafeNull(7L)).thenReturn(Optional.empty());

    listener.userSaved(new UserSavedEvent(user));

    verify(subscriptions).deleteById(13L);
    verifyNoInteractions(permissions);
  }

  private enum RevocationEvent {
    ITEM_EDITED,
    ITEM_TRANSFERRED,
    USER_SAVED,
    GROUP_CHANGED,
    COMMUNITY_CHANGED
  }
}
