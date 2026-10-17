package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarCurrentAccessDao;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves Booking access from the current Inventory permission of its target item. */
@Component
public class BookingItemPermissions {

  private final InventoryPermissionUtils inventoryPermissions;
  private final InstrumentDao instruments;
  private final BookingResourceRoleScheme roleScheme;
  private final BookingCalendarSubscriptionDao subscriptions;
  private final BookingCalendarCurrentAccessDao currentAccess;
  private final BookingCalendarPermissionGateDao permissionGate;

  public BookingItemPermissions(
      InventoryPermissionUtils inventoryPermissions,
      InstrumentDao instruments,
      BookingResourceRoleScheme roleScheme,
      BookingCalendarSubscriptionDao subscriptions,
      BookingCalendarCurrentAccessDao currentAccess,
      BookingCalendarPermissionGateDao permissionGate) {
    this.inventoryPermissions = inventoryPermissions;
    this.instruments = instruments;
    this.roleScheme = roleScheme;
    this.subscriptions = subscriptions;
    this.currentAccess = currentAccess;
    this.permissionGate = permissionGate;
  }

  /**
   * Resolves fresh membership facts while serializing the mutation with permission revocation.
   *
   * <p>The caller's locking configuration lookup acquires the shared permission gate before the
   * target and configuration locks. This method then takes the legacy fence before reading current
   * scalar authorization facts.
   */
  public ResolvedResourceAccess resolveForMutation(
      BookingConfiguration configuration, User subject) {
    if (subject == null || subject.getId() == null) return ResolvedResourceAccess.none();
    permissionGate.requireHeld(BookingCalendarPermissionGateDao.Mode.SHARED);
    subscriptions.lockPermissionFence();
    BookableTargetReference target = configuration.getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT || target.id() == null) {
      return ResolvedResourceAccess.none();
    }
    return resolved(currentAccess.resolve(subject.getId(), target.id()));
  }

  /** Resolves one configuration against the target item's current permission state. */
  @Transactional(readOnly = true)
  public ResolvedResourceAccess resolve(BookingConfiguration configuration, User subject) {
    return resolve(configuration == null ? null : configuration.getTarget(), subject);
  }

  /** Resolves a target reference against the item's current permission state. */
  @Transactional(readOnly = true)
  public ResolvedResourceAccess resolve(BookableTargetReference target, User subject) {
    if (!eligibleSubject(subject) || target == null) {
      return ResolvedResourceAccess.none();
    }
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
    boolean eligible = eligibleSubject(subject);
    Set<Long> targetIds =
        configurations.stream()
            .map(BookingConfiguration::getTarget)
            .filter(target -> target != null && target.type() == BookableTargetType.INSTRUMENT)
            .map(BookableTargetReference::id)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, InstrumentDao.BookingItemAccess> targets =
        eligible && !targetIds.isEmpty()
            ? instruments.getBookingItemAccess(targetIds, subject)
            : Map.of();
    Map<Long, ResolvedResourceAccess> resolved = new LinkedHashMap<>();
    for (BookingConfiguration configuration : configurations) {
      if (configuration.getId() == null) {
        throw new IllegalArgumentException("Persisted booking configuration id is required");
      }
      BookableTargetReference target = configuration.getTarget();
      InstrumentDao.BookingItemAccess access =
          target == null || target.type() != BookableTargetType.INSTRUMENT
              ? null
              : targets.get(target.id());
      resolved.put(
          configuration.getId(),
          access != null ? resolve(access, subject) : ResolvedResourceAccess.none());
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
      return owner();
    }
    if (inventoryPermissions.canUserEditInventoryRecord(instrument, subject)) {
      return role(BookingResourceRoleScheme.BOOKER);
    }
    return role(BookingResourceRoleScheme.VIEWER);
  }

  private ResolvedResourceAccess resolve(InstrumentDao.BookingItemAccess access, User subject) {
    boolean owner =
        subject.hasSysadminRole()
            || (access.ownerUsername() != null
                && access.ownerUsername().equals(subject.getUsername()));
    if (owner) {
      return owner();
    }
    return access.directEdit()
        ? role(BookingResourceRoleScheme.BOOKER)
        : role(BookingResourceRoleScheme.VIEWER);
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

  private ResolvedResourceAccess owner() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.OWNER),
        withoutAclManagement(roleScheme.capabilities(BookingResourceRoleScheme.OWNER)),
        List.of(ResourceRoleSource.implicit(BookingResourceRoleScheme.OWNER)));
  }

  private ResolvedResourceAccess resolved(BookingCalendarCurrentAccessDao.Access access) {
    return switch (access) {
      case OWNER -> owner();
      case BOOKER -> role(BookingResourceRoleScheme.BOOKER);
      case VIEWER -> role(BookingResourceRoleScheme.VIEWER);
      case DENIED -> ResolvedResourceAccess.none();
    };
  }

  private static Set<String> withoutAclManagement(Set<String> capabilities) {
    return capabilities.stream()
        .filter(
            capability ->
                !capability.equals(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS)
                    && !capability.equals(BookingResourceRoleScheme.MANAGE_OWNERS))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean eligibleSubject(User subject) {
    return subject != null && subject.isEnabled() && !subject.isAccountLocked();
  }
}
