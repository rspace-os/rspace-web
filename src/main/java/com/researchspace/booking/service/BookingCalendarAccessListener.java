package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.model.UserSavedEvent;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.service.resourceaccess.ResourceAccessChangedEvent;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Revokes item bearer links at access loss, even when no feed request observes that loss. Events
 * run synchronously inside the publishing DAO/service transaction.
 */
@Component
@RequiredArgsConstructor
public class BookingCalendarAccessListener {
  private final BookingCalendarSubscriptionDao subscriptions;
  private final ResourceAccessManager access;

  @EventListener
  public void userSaved(UserSavedEvent event) {
    var owned = subscriptions.findByUserId(event.user().getId());
    if (owned.isEmpty()) return;
    var resolved =
        access.resolveAll(
            owned.stream()
                .map(subscription -> subscription.getBookingConfiguration().getResourceAccess())
                .toList(),
            event.user());
    for (var subscription : owned) {
      if (!resolved
          .get(subscription.getBookingConfiguration().getResourceAccess().getId())
          .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
        subscriptions.remove(subscription.getId());
      }
    }
  }

  @EventListener
  public void assignmentsChanged(ResourceAccessChangedEvent event) {
    if (!(event.protectedResource() instanceof BookingConfiguration configuration)) return;
    for (var subscription : subscriptions.findByConfigurationId(configuration.getId())) {
      if (!access
          .resolve(configuration.getResourceAccess(), subscription.getUser())
          .hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
        subscriptions.remove(subscription.getId());
      }
    }
  }
}
