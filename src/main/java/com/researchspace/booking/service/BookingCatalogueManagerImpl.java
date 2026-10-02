package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.FeatureFlagManager;
import jakarta.ws.rs.NotFoundException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional implementation of the dedicated Booking catalogue. */
@Service
@Transactional(readOnly = true)
public class BookingCatalogueManagerImpl implements BookingCatalogueManager {

  /** The configuration target reference in the catalogue's configuration query. */
  private static final String CONFIGURATION_TARGET = "bookingConfiguration.target";

  /** Configurations classified per availability batch, and so event query bind values. */
  static final int AVAILABILITY_BATCH_SIZE = 500;

  private final BookingConfigurationManager configurations;
  private final InstrumentDao instruments;
  private final FeatureFlagManager featureFlags;
  private final com.researchspace.booking.dao.BookingItemQuery itemQuery;
  private final com.researchspace.booking.dao.BookingCalendarQuery calendarQuery;
  private final BookingLocationFilterManager locations;
  private final TimeSlotBookingDao bookings;

  public BookingCatalogueManagerImpl(
      BookingConfigurationManager configurations,
      InstrumentDao instruments,
      FeatureFlagManager featureFlags,
      com.researchspace.booking.dao.BookingItemQuery itemQuery,
      com.researchspace.booking.dao.BookingCalendarQuery calendarQuery,
      BookingLocationFilterManager locations,
      TimeSlotBookingDao bookings) {
    this.configurations = configurations;
    this.instruments = instruments;
    this.featureFlags = featureFlags;
    this.itemQuery = itemQuery;
    this.calendarQuery = calendarQuery;
    this.locations = locations;
    this.bookings = bookings;
  }

  @Override
  public Page search(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      int page,
      int limit,
      User caller) {
    return search(
        query,
        targetGlobalId,
        request,
        targetTypes,
        locationGlobalIds,
        capability,
        ownedByCaller,
        null,
        null,
        page,
        limit,
        caller);
  }

  @Override
  public Page search(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      Availability availability,
      AvailabilityWindow window,
      int page,
      int limit,
      User caller) {
    Optional<Scope> scope =
        scope(
            query,
            targetGlobalId,
            request,
            targetTypes,
            locationGlobalIds,
            capability,
            ownedByCaller,
            caller,
            null);
    if (scope.isEmpty()) {
      return emptyPage(page, limit);
    }
    if (availability == null) {
      return page(scope.get(), page, limit, caller);
    }
    java.util.Objects.requireNonNull(window, "An availability filter needs its window");
    long firstRow = (long) (page - 1) * limit;
    List<BookingConfiguration> rows = new ArrayList<>();
    long[] total = {0};
    scanAvailability(
        scope.get(),
        window,
        caller,
        (configuration, category) -> {
          if (category != availability) {
            return;
          }
          if (total[0] >= firstRow && rows.size() < limit) {
            rows.add(configuration);
          }
          total[0]++;
        });
    return page(rows, page, limit, total[0], caller);
  }

  @Override
  public AvailabilityCounts countAvailability(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      AvailabilityWindow window,
      User caller) {
    Optional<Scope> scope =
        scope(
            query,
            targetGlobalId,
            request,
            targetTypes,
            locationGlobalIds,
            capability,
            ownedByCaller,
            caller,
            null);
    Map<Availability, Long> counts = new EnumMap<>(Availability.class);
    scope.ifPresent(
        value ->
            scanAvailability(
                value,
                window,
                caller,
                (ignored, category) -> counts.merge(category, 1L, Long::sum)));
    return new AvailabilityCounts(
        counts.getOrDefault(Availability.AVAILABLE_NOW, 0L),
        counts.getOrDefault(Availability.FREE_LATER_TODAY, 0L));
  }

  @Override
  public Page searchCalendar(
      String query,
      ResourceRequest items,
      ResourceRequest events,
      java.time.Instant start,
      java.time.Instant end,
      boolean ownedByCaller,
      int page,
      int limit,
      User caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) {
      throw new NotFoundException();
    }
    return scope(
            null,
            null,
            items,
            List.of(),
            List.of(),
            null,
            ownedByCaller,
            caller,
            calendarEvents(events, start, end, query, caller))
        .map(scope -> page(scope, page, limit, caller))
        .orElseGet(() -> emptyPage(page, limit));
  }

  /** The Calendar event scope, answering top-level event location rules without item lists. */
  private com.researchspace.dao.query.RsqlCollectionQuery.Predicate calendarEvents(
      ResourceRequest events,
      java.time.Instant start,
      java.time.Instant end,
      String query,
      User caller) {
    BookingLocationFilterManager.Resolved resolved =
        locations.resolveLocations(
            events,
            caller,
            com.researchspace.booking.dao.BookingCalendarQuery.RESOURCE_EVENT_TARGET);
    return calendarQuery.resources(
        resolved.request(), resolved.restriction(), start, end, query, caller);
  }

  /** Every catalogue filter of one request, before paging. */
  private record Scope(
      ResourceRequest request,
      com.researchspace.dao.query.RsqlCollectionQuery.Predicate restriction) {

    ResourceRequest page(int page, int limit) {
      return new ResourceRequest(
          request.filter(),
          request.serverConstraint(),
          request.sort(),
          new ResourceRequest.Page(page, limit),
          request.fieldSelections(),
          request.includes(),
          request.runtime());
    }
  }

  /** The complete scope, or empty when a filter can already match nothing. */
  private Optional<Scope> scope(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      User caller,
      com.researchspace.dao.query.RsqlCollectionQuery.Predicate restriction) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) {
      throw new NotFoundException();
    }
    if (!targetTypes.isEmpty() && !targetTypes.contains("INSTRUMENT")) {
      return Optional.empty();
    }
    BookingLocationFilterManager.Resolved resolved =
        locations.resolveLocations(request, caller, CONFIGURATION_TARGET);
    request = resolved.request();
    restriction =
        com.researchspace.booking.dao.BookingItemQuery.and(restriction, resolved.restriction());

    List<FilterExpression> filters = new ArrayList<>();
    filters.add(comparison("enabled", Operator.EQUAL, true));
    filters.add(comparison("state", Operator.EQUAL, BookingConfigurationState.ACTIVE));
    filters.add(comparison("target.deleted", Operator.EQUAL, false));
    if (capability != null) {
      restriction =
          com.researchspace.booking.dao.BookingItemQuery.and(
              restriction,
              itemQuery.restriction(
                  caller, true, capability == Capability.CREATE_BLOCKOUT, CONFIGURATION_TARGET));
    }
    if (ownedByCaller) {
      restriction =
          com.researchspace.booking.dao.BookingItemQuery.and(
              restriction, itemQuery.ownedBy(caller, CONFIGURATION_TARGET));
    }
    if (query != null && !query.isBlank()) {
      Set<Long> matchingTargetIds =
          new HashSet<>(instruments.searchBookingCatalogueTargetIds(query, caller));
      ResourceReference<BookableTargetType, Long> globalIdTarget =
          instrumentReference(query.trim().toUpperCase(java.util.Locale.ROOT));
      if (globalIdTarget != null) {
        matchingTargetIds.add(globalIdTarget.id());
      }
      if (matchingTargetIds.isEmpty()) {
        return Optional.empty();
      }
      filters.add(
          new FilterExpression.Comparison(
              "target.value", Operator.IN, List.copyOf(matchingTargetIds), false));
    }
    if (targetGlobalId != null && !targetGlobalId.isBlank()) {
      ResourceReference<BookableTargetType, Long> target = instrumentReference(targetGlobalId);
      if (target == null) {
        return Optional.empty();
      }
      filters.add(comparison("target", Operator.EQUAL, target));
    }
    if (!locationGlobalIds.isEmpty()) {
      List<ResourceReference<BookableTargetType, Long>> candidateTargets =
          visibleLocationTargets(locationGlobalIds, caller);
      if (candidateTargets.isEmpty()) {
        return Optional.empty();
      }
      filters.add(
          new FilterExpression.Comparison(
              "target", Operator.IN, List.copyOf(candidateTargets), false));
    }

    return Optional.of(
        new Scope(
            new ResourceRequest(
                    request.filter(),
                    request.serverConstraint(),
                    List.of(),
                    new ResourceRequest.Page(1, 1),
                    ResourceFieldSelections.root(FieldSelection.all()),
                    IncludeTree.empty(),
                    request.runtime())
                .restrict(new FilterExpression.And(filters)),
            restriction));
  }

  private ResourcePage<BookingConfiguration> configurations(
      Scope scope, ResourceRequest request, User caller) {
    return scope.restriction() == null
        ? configurations.getConfigurations(request, caller)
        : configurations.getConfigurations(request, caller, scope.restriction());
  }

  private Page page(Scope scope, int page, int limit, User caller) {
    ResourcePage<BookingConfiguration> result =
        configurations(scope, scope.page(page, limit), caller);
    return page(result.resources(), page, limit, result.total(), caller);
  }

  private Page page(
      List<BookingConfiguration> configurations, int page, int limit, long total, User caller) {
    Set<Long> targetIds =
        configurations.stream()
            .map(configuration -> configuration.getTarget().id())
            .collect(Collectors.toSet());
    Map<Long, Instrument> targets = instruments.getBookingRelationshipTargets(targetIds);
    Map<Long, InstrumentParentLocationSummary> locations =
        instruments.getReadableParentLocationSummaries(targetIds, caller);
    return new Page(
        configurations.stream()
            .map(
                configuration ->
                    item(
                        configuration,
                        targets.get(configuration.getTarget().id()),
                        locations.get(configuration.getTarget().id())))
            .flatMap(java.util.Optional::stream)
            .toList(),
        page,
        limit,
        total,
        new Facets(total == 0 ? List.of() : List.of("INSTRUMENT")));
  }

  /**
   * Classifies every item in the scope, in catalogue order, reading configurations and their events
   * one keyset batch at a time so memory and bound parameters stay bounded.
   */
  private void scanAvailability(
      Scope scope,
      AvailabilityWindow window,
      User caller,
      java.util.function.BiConsumer<BookingConfiguration, Availability> visitor) {
    long after = 0;
    while (true) {
      ResourceRequest batch =
          new Scope(
                  scope.request().restrict(comparison("id", Operator.GREATER_THAN, after)),
                  scope.restriction())
              .page(1, AVAILABILITY_BATCH_SIZE);
      List<BookingConfiguration> found = configurations(scope, batch, caller).resources();
      Map<Long, List<TimeSlotBookingDao.EventInterval>> events = events(found, window);
      for (BookingConfiguration configuration : found) {
        BookingCurrentAvailability.classify(
                configuration, events.getOrDefault(configuration.getId(), List.of()), window)
            .ifPresent(category -> visitor.accept(configuration, category));
      }
      if (found.size() < AVAILABILITY_BATCH_SIZE) {
        return;
      }
      after = found.get(found.size() - 1).getId();
    }
  }

  /** Confirmed events that can reach the window once widened by any item's buffers. */
  private Map<Long, List<TimeSlotBookingDao.EventInterval>> events(
      List<BookingConfiguration> found, AvailabilityWindow window) {
    if (found.isEmpty() || !window.start().isBefore(window.end())) {
      return Map.of();
    }
    long before =
        found.stream().mapToLong(BookingConfiguration::getBufferBeforeMinutes).max().orElse(0);
    long after =
        found.stream().mapToLong(BookingConfiguration::getBufferAfterMinutes).max().orElse(0);
    return bookings
        .findConfirmedEventIntervals(
            found.stream().map(BookingConfiguration::getId).toList(),
            java.util.Date.from(window.start().minus(after, ChronoUnit.MINUTES)),
            java.util.Date.from(window.end().plus(before, ChronoUnit.MINUTES)))
        .stream()
        .collect(Collectors.groupingBy(TimeSlotBookingDao.EventInterval::configurationId));
  }

  @Override
  public LocationPage searchLocations(
      String query,
      List<String> targetTypes,
      List<String> globalIds,
      int page,
      int limit,
      User caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) {
      throw new NotFoundException();
    }
    if (!targetTypes.isEmpty() && !targetTypes.contains("INSTRUMENT")) {
      return new LocationPage(List.of(), page, limit, 0);
    }
    Set<Long> containerIds = globalIds.isEmpty() ? null : locationIds(globalIds);
    String nameQuery = query;
    Set<Long> queriedId = query == null ? null : locationIds(List.of(query.trim()));
    if (queriedId != null && !queriedId.isEmpty()) {
      nameQuery = null;
      containerIds =
          containerIds == null
              ? queriedId
              : containerIds.stream().filter(queriedId::contains).collect(Collectors.toSet());
    }
    if (containerIds != null && containerIds.isEmpty()) {
      return new LocationPage(List.of(), page, limit, 0);
    }
    ResourcePage<com.researchspace.model.inventory.InstrumentParentLocationSummary> result =
        instruments.getBookingCatalogueLocations(nameQuery, containerIds, page, limit, caller);
    return new LocationPage(
        result.resources().stream()
            .map(BookingLocationFilterManagerImpl::location)
            .map(location -> new Location(location.globalId(), location.name()))
            .toList(),
        page,
        limit,
        result.total());
  }

  /** Container IDs named by {@code IC} or {@code BE} global IDs; other values name nothing. */
  private static Set<Long> locationIds(List<String> globalIds) {
    Set<Long> ids = new HashSet<>();
    for (String globalId : globalIds) {
      if (globalId == null
          || !GlobalIdentifier.isValid(globalId.toUpperCase(java.util.Locale.ROOT))) {
        continue;
      }
      GlobalIdentifier identifier =
          new GlobalIdentifier(globalId.toUpperCase(java.util.Locale.ROOT));
      if ((identifier.getPrefix() == GlobalIdPrefix.IC
              || identifier.getPrefix() == GlobalIdPrefix.BE)
          && !identifier.hasVersionId()) {
        ids.add(identifier.getDbId());
      }
    }
    return ids;
  }

  private static Page emptyPage(int page, int limit) {
    return new Page(List.of(), page, limit, 0, new Facets(List.of()));
  }

  private List<ResourceReference<BookableTargetType, Long>> visibleLocationTargets(
      List<String> globalIds, User caller) {
    Set<Long> containerIds;
    Set<Long> workbenchIds;
    try {
      List<GlobalIdentifier> identifiers =
          globalIds.stream()
              .map(GlobalIdentifier::new)
              .filter(
                  id -> id.getPrefix() == GlobalIdPrefix.IC || id.getPrefix() == GlobalIdPrefix.BE)
              .toList();
      containerIds =
          identifiers.stream()
              .filter(id -> id.getPrefix() == GlobalIdPrefix.IC)
              .map(GlobalIdentifier::getDbId)
              .collect(Collectors.toSet());
      workbenchIds =
          identifiers.stream()
              .filter(id -> id.getPrefix() == GlobalIdPrefix.BE)
              .map(GlobalIdentifier::getDbId)
              .collect(Collectors.toSet());
    } catch (IllegalArgumentException invalid) {
      return List.of();
    }
    return instruments.findByReadableImmediateParentIds(containerIds, workbenchIds, caller).stream()
        .map(id -> new ResourceReference<>(BookableTargetType.INSTRUMENT, id))
        .toList();
  }

  private static ResourceReference<BookableTargetType, Long> instrumentReference(String globalId) {
    try {
      GlobalIdentifier identifier = new GlobalIdentifier(globalId);
      return identifier.getPrefix() == GlobalIdPrefix.IN
          ? new ResourceReference<>(BookableTargetType.INSTRUMENT, identifier.getDbId())
          : null;
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private java.util.Optional<Item> item(
      BookingConfiguration configuration,
      Instrument instrument,
      InstrumentParentLocationSummary parent) {
    if (instrument == null || instrument.isDeleted()) {
      return java.util.Optional.empty();
    }
    Location location =
        parent == null
            ? null
            : new Location(
                (parent.containerType() == Container.ContainerType.WORKBENCH
                        ? GlobalIdPrefix.BE
                        : GlobalIdPrefix.IC)
                    + parent.containerId().toString(),
                parent.containerName());
    return java.util.Optional.of(
        new Item(
            configuration.getId(),
            configuration.getConfigurationVersion(),
            "INSTRUMENT",
            instrument.getId(),
            instrument.getGlobalIdentifier(),
            instrument.getName(),
            configuration.getTimeZone(),
            configuration.getSlotGranularityMinutes(),
            configuration.getOpeningStart(),
            configuration.getOpeningEnd(),
            configuration.getOpenDays(),
            configuration.getOpeningExceptions(),
            configuration.getBufferBeforeMinutes(),
            configuration.getBufferAfterMinutes(),
            configuration.getMaxBookingDurationMinutes(),
            configuration.isAllowDoubleBooking(),
            configuration.getEffectiveRole(),
            configuration.getCapabilities(),
            location));
  }

  private static FilterExpression.Comparison comparison(
      String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }
}
