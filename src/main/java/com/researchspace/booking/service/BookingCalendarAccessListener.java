package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarSubscriptionCandidate;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.UserSavedEvent;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Revokes item links after their Inventory permission source transaction commits.
 *
 * <p>Paths that touch a configuration acquire its target/configuration lock before the shared
 * permission fence. This is the lock order used by booking mutations as well.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class BookingCalendarAccessListener {
  private final BookingCalendarSubscriptionDao subscriptions;
  private final BookingConfigurationDao configurations;
  private final UserDao users;
  private final BookingItemPermissions permissions;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void userSaved(UserSavedEvent event) {
    subscriptions.lockPermissionFence();
    revokeUnreadable(subscriptions.findCandidatesByUserId(event.user().getId()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void itemEdited(InventoryEditingEvent event) {
    itemChanged(event.getEditedItem());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void itemDeleted(InventoryDeleteEvent event) {
    itemChanged(event.getDeletedItem());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void itemTransferred(InventoryTransferEvent event) {
    itemChanged(event.getTransferredItem());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void groupChanged(com.researchspace.model.GroupPermissionsChangedEvent event) {
    subscriptions.lockPermissionFence();
    revokeUnreadable(subscriptions.findCandidatesForMembershipRevalidation());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void communityChanged(com.researchspace.model.CommunityPermissionsChangedEvent event) {
    subscriptions.lockPermissionFence();
    revokeUnreadable(subscriptions.findCandidatesForMembershipRevalidation());
  }

  private void itemChanged(InventoryRecord item) {
    if (!(item instanceof com.researchspace.model.inventory.Instrument instrument)
        || instrument.isTemplate()) return;
    configurations
        .lockByTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, item.getId()))
        .ifPresent(
            configuration -> {
              subscriptions.lockPermissionFence();
              revokeUnreadable(
                  subscriptions.findCandidatesByConfigurationId(configuration.getId()));
            });
  }

  private void revokeUnreadable(List<BookingCalendarSubscriptionCandidate> candidates) {
    var byUser =
        candidates.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    BookingCalendarSubscriptionCandidate::userId));
    Map<Long, com.researchspace.model.booking.BookingConfiguration> configurationsById =
        new LinkedHashMap<>();
    candidates.stream()
        .map(BookingCalendarSubscriptionCandidate::configurationId)
        .distinct()
        .forEach(
            id ->
                configurations
                    .getSafeNull(id)
                    .ifPresent(value -> configurationsById.put(id, value)));
    byUser.forEach(
        (userId, owned) -> {
          var user = users.getSafeNull(userId);
          if (user.isEmpty()) {
            owned.forEach(subscription -> subscriptions.deleteById(subscription.subscriptionId()));
            return;
          }
          var resolved =
              permissions.resolveAll(
                  owned.stream()
                      .map(candidate -> configurationsById.get(candidate.configurationId()))
                      .filter(java.util.Objects::nonNull)
                      .distinct()
                      .toList(),
                  user.get());
          for (var subscription : owned) {
            var access = resolved.get(subscription.configurationId());
            if (access == null || !access.hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
              subscriptions.deleteById(subscription.subscriptionId());
            }
          }
        });
  }
}
