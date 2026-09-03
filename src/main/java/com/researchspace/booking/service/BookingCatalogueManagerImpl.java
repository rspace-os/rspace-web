package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional implementation of the dedicated Booking catalogue. */
@Service
@Transactional(readOnly = true)
public class BookingCatalogueManagerImpl implements BookingCatalogueManager {

  private final BookingConfigurationManager configurations;
  private final InstrumentDao instruments;
  private final FeatureFlagManager featureFlags;
  private final BookingResourceRoleScheme roleScheme;

  public BookingCatalogueManagerImpl(
      BookingConfigurationManager configurations,
      InstrumentDao instruments,
      FeatureFlagManager featureFlags,
      BookingResourceRoleScheme roleScheme) {
    this.configurations = configurations;
    this.instruments = instruments;
    this.featureFlags = featureFlags;
    this.roleScheme = roleScheme;
  }

  @Override
  public Page search(
      String query,
      String targetGlobalId,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      int page,
      int limit,
      User caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) {
      throw new NotFoundException();
    }
    if (!targetTypes.isEmpty() && !targetTypes.contains("INSTRUMENT")) {
      return emptyPage(page, limit);
    }

    List<FilterExpression> filters = new ArrayList<>();
    filters.add(comparison("enabled", Operator.EQUAL, true));
    filters.add(comparison("state", Operator.EQUAL, BookingConfigurationState.ACTIVE));
    filters.add(comparison("target.deleted", Operator.EQUAL, false));
    if (query != null && !query.isBlank()) {
      filters.add(comparison("target.name", Operator.CONTAINS, query.trim()));
    }
    if (targetGlobalId != null && !targetGlobalId.isBlank()) {
      ResourceReference<BookableTargetType, Long> target = instrumentReference(targetGlobalId);
      if (target == null) {
        return emptyPage(page, limit);
      }
      filters.add(comparison("target", Operator.EQUAL, target));
    }
    if (!locationGlobalIds.isEmpty()) {
      List<ResourceReference<BookableTargetType, Long>> candidateTargets =
          visibleLocationTargets(locationGlobalIds, caller);
      if (candidateTargets.isEmpty()) {
        return emptyPage(page, limit);
      }
      filters.add(
          new FilterExpression.Comparison(
              "target", Operator.IN, List.copyOf(candidateTargets), false));
    }

    ResourcePage<BookingConfiguration> result =
        configurations.getConfigurations(
            new ResourceRequest(
                new FilterExpression.And(filters),
                List.of(),
                new ResourceRequest.Page(page, limit),
                FieldSelection.all(),
                IncludeTree.empty()),
            caller);
    Set<Long> targetIds =
        result.resources().stream()
            .map(configuration -> configuration.getTarget().id())
            .collect(Collectors.toSet());
    Map<Long, Instrument> targets = instruments.getBookingRelationshipTargets(targetIds);
    Map<Long, InstrumentParentLocationSummary> locations =
        instruments.getReadableParentLocationSummaries(targetIds, caller);
    return new Page(
        result.resources().stream()
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
        result.total(),
        new Facets(List.of("INSTRUMENT")));
  }

  @Override
  public LocationPage searchLocations(
      String query, List<String> targetTypes, int page, int limit, User caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller)) {
      throw new NotFoundException();
    }
    if (!targetTypes.isEmpty() && !targetTypes.contains("INSTRUMENT")) {
      return new LocationPage(List.of(), page, limit, 0);
    }
    ResourcePage<com.researchspace.model.inventory.InstrumentParentLocationSummary> result =
        instruments.getBookingCatalogueLocations(
            query,
            page,
            limit,
            caller,
            roleScheme.rolesWithCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY));
    return new LocationPage(
        result.resources().stream()
            .map(
                location ->
                    new Location(
                        (location.containerType() == Container.ContainerType.WORKBENCH
                                ? GlobalIdPrefix.BE
                                : GlobalIdPrefix.IC)
                            + location.containerId().toString(),
                        location.containerName()))
            .toList(),
        page,
        limit,
        result.total());
  }

  private static Page emptyPage(int page, int limit) {
    return new Page(List.of(), page, limit, 0, new Facets(List.of("INSTRUMENT")));
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
