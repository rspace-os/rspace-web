package com.researchspace.booking.service;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves Booking access from the current Inventory permission of its target item. */
@Component
public class BookingItemPermissions {

  @org.springframework.beans.factory.annotation.Autowired
  private com.researchspace.booking.dao.BookingCalendarSubscriptionDao subscriptions;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.beans.factory.ObjectProvider<FreshBookingPermissionReader>
      freshReader;

  private final InventoryPermissionUtils inventoryPermissions;
  private final InstrumentDao instruments;
  private final BookingResourceRoleScheme roleScheme;

  public BookingItemPermissions(
      InventoryPermissionUtils inventoryPermissions,
      InstrumentDao instruments,
      BookingResourceRoleScheme roleScheme) {
    this.inventoryPermissions = inventoryPermissions;
    this.instruments = instruments;
    this.roleScheme = roleScheme;
  }

  /**
   * Resolves fresh membership facts while serializing the mutation with permission revocation.
   *
   * <p>The caller must already hold the booking configuration lock. The shared permission fence is
   * acquired second so all booking mutation paths use the same configuration-to-fence order.
   */
  public ResolvedResourceAccess resolveForMutation(
      BookingConfiguration configuration, User subject) {
    if (subject == null || subject.getId() == null) return ResolvedResourceAccess.none();
    subscriptions.lockPermissionFence();
    return freshReader.getObject().resolve(configuration.getTarget(), subject.getId());
  }

  /** Resolves one configuration against the target item's current permission state. */
  @Transactional(readOnly = true)
  public ResolvedResourceAccess resolve(BookingConfiguration configuration, User subject) {
    if (!eligibleSubject(subject) || configuration == null || configuration.getTarget() == null) {
      return ResolvedResourceAccess.none();
    }
    BookableTargetReference target = configuration.getTarget();
    if (target.type() != BookableTargetType.INSTRUMENT || target.id() == null) {
      return ResolvedResourceAccess.none();
    }
    return instruments
        .getSafeNull(target.id())
        .filter(instrument -> readableInstrument(instrument, subject))
        .map(instrument -> resolve(instrument, subject))
        .orElseGet(ResolvedResourceAccess::none);
  }

  /** Resolves a bounded page without one target lookup per configuration. */
  public Map<Long, ResolvedResourceAccess> resolveAll(
      Collection<BookingConfiguration> configurations, User subject) {
    if (configurations.isEmpty()) {
      return Map.of();
    }
    Map<Long, Instrument> targets = new LinkedHashMap<>();
    Set<Long> targetIds =
        configurations.stream()
            .map(BookingConfiguration::getTarget)
            .filter(target -> target != null && target.type() == BookableTargetType.INSTRUMENT)
            .map(BookableTargetReference::id)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    if (!targetIds.isEmpty()) {
      targets.putAll(instruments.getBookingRelationshipTargets(targetIds));
    }
    Map<Long, ResolvedResourceAccess> resolved = new LinkedHashMap<>();
    for (BookingConfiguration configuration : configurations) {
      if (configuration.getId() == null) {
        throw new IllegalArgumentException("Persisted booking configuration id is required");
      }
      BookableTargetReference target = configuration.getTarget();
      Instrument instrument =
          target == null || target.type() != BookableTargetType.INSTRUMENT
              ? null
              : targets.get(target.id());
      resolved.put(
          configuration.getId(),
          eligibleSubject(subject) && readableInstrument(instrument, subject)
              ? resolve(instrument, subject)
              : ResolvedResourceAccess.none());
    }
    return Map.copyOf(resolved);
  }

  private ResolvedResourceAccess resolve(Instrument instrument, User subject) {
    boolean owner =
        subject.hasSysadminRole()
            || (instrument.getOwner() != null
                && instrument.getOwner().getUsername() != null
                && instrument.getOwner().getUsername().equals(subject.getUsername()));
    if (owner) {
      return new ResolvedResourceAccess(
          Optional.of(BookingResourceRoleScheme.OWNER),
          withoutAclManagement(roleScheme.capabilities(BookingResourceRoleScheme.OWNER)),
          List.of(ResourceRoleSource.implicit(BookingResourceRoleScheme.OWNER)));
    }
    if (inventoryPermissions.canUserEditInventoryRecord(instrument, subject)) {
      return role(BookingResourceRoleScheme.BOOKER);
    }
    return role(BookingResourceRoleScheme.VIEWER);
  }

  private boolean readableInstrument(Instrument instrument, User subject) {
    return instrument != null
        && !instrument.isDeleted()
        && !instrument.isTemplate()
        && instrument.getOwner() != null
        && (subject.hasSysadminRole()
            || inventoryPermissions.canUserReadInventoryRecord(instrument, subject));
  }

  private ResolvedResourceAccess role(String role) {
    return new ResolvedResourceAccess(
        Optional.of(role),
        roleScheme.capabilities(role),
        List.of(ResourceRoleSource.implicit(role)));
  }

  private static Set<String> withoutAclManagement(Set<String> capabilities) {
    return capabilities.stream()
        .filter(
            capability ->
                !capability.equals(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS)
                    && !capability.equals(BookingResourceRoleScheme.MANAGE_OWNERS))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static boolean eligibleSubject(User subject) {
    return subject != null && subject.isEnabled() && !subject.isAccountLocked();
  }
}
