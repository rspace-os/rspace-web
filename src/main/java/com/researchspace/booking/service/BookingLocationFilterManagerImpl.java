package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingItemQuery;
import com.researchspace.booking.dao.BookingLocationQuery;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Rewrites Booking location filters through the Inventory readable-parent query. */
@Service("bookingLocationFilterManager")
public class BookingLocationFilterManagerImpl implements BookingLocationFilterManager {

  /** Bound values per rewritten comparison, well below the JDBC placeholder limit. */
  static final int MAX_LOCATION_ITEMS = 10_000;

  private static final Set<Operator> CORRELATED_OPERATORS =
      Set.of(Operator.EQUAL, Operator.IN, Operator.NOT_EQUAL, Operator.NOT_IN, Operator.EXISTS);

  private final InstrumentDao instruments;
  private final InstrumentReadAccess instrumentReadAccess;
  private final BookingLocationQuery locationQuery;

  public BookingLocationFilterManagerImpl(
      InstrumentDao instruments,
      InstrumentReadAccess instrumentReadAccess,
      BookingLocationQuery locationQuery) {
    this.instruments = instruments;
    this.instrumentReadAccess = instrumentReadAccess;
    this.locationQuery = locationQuery;
  }

  @Override
  public Resolved resolveLocations(ResourceRequest request, User caller, String targetPath) {
    FilterExpression filter = request.filter();
    if (filter == null || !mentionsLocation(filter)) {
      return new Resolved(request, null);
    }
    List<FilterExpression> remaining = new ArrayList<>();
    RsqlCollectionQuery.Predicate restriction = null;
    AccessConstraints constraints = null;
    int hoisted = 0;
    // Item and event restrictions can share one query, so names follow the target path.
    String names = targetPath.replace('.', '_') + "_location";
    for (FilterExpression conjunct : conjuncts(filter)) {
      if (conjunct instanceof FilterExpression.Comparison comparison
          && ApiV2BookingLocationResource.isLocationSelector(comparison.field())
          && CORRELATED_OPERATORS.contains(comparison.operator())) {
        if (constraints == null) {
          constraints = accessConstraints(caller);
        }
        restriction =
            BookingItemQuery.and(
                restriction,
                correlated(comparison, caller, targetPath, names + hoisted++, constraints));
      } else {
        remaining.add(conjunct);
      }
    }
    if (hoisted == 0) {
      return new Resolved(resolveLocations(request, caller), null);
    }
    FilterExpression rest =
        remaining.isEmpty()
            ? null
            : remaining.size() == 1 ? remaining.get(0) : new FilterExpression.And(remaining);
    ResourceRequest listed =
        new ResourceRequest(
            rest,
            request.serverConstraint(),
            request.sort(),
            request.page(),
            request.fieldSelections(),
            request.includes(),
            request.runtime());
    return new Resolved(resolveLocations(listed, caller), restriction);
  }

  /** The correlated SQL form of one location comparison with a correlated operator. */
  private RsqlCollectionQuery.Predicate correlated(
      FilterExpression.Comparison comparison,
      User caller,
      String targetPath,
      String name,
      AccessConstraints constraints) {
    return switch (comparison.operator()) {
      case EQUAL, IN ->
          locationQuery.readableParent(
              caller,
              targetPath,
              containerIds(comparison),
              true,
              name,
              constraints.read(),
              constraints.edit());
      case NOT_EQUAL, NOT_IN ->
          locationQuery.readableParent(
              caller,
              targetPath,
              containerIds(comparison),
              false,
              name,
              constraints.read(),
              constraints.edit());
      default ->
          locationQuery.readableParent(
              caller,
              targetPath,
              null,
              Boolean.TRUE.equals(comparison.values().get(0)),
              name,
              constraints.read(),
              constraints.edit());
    };
  }

  private AccessConstraints accessConstraints(User caller) {
    if (caller == null || !caller.isEnabled() || caller.isAccountLocked()) {
      return new AccessConstraints(null, null);
    }
    return new AccessConstraints(
        instrumentReadAccess.constraint(caller, false),
        instrumentReadAccess.constraint(caller, true));
  }

  private record AccessConstraints(FilterExpression read, FilterExpression edit) {}

  /** The conjuncts of a filter, looking through nested conjunctions. */
  private static List<FilterExpression> conjuncts(FilterExpression filter) {
    if (filter instanceof FilterExpression.And and) {
      return and.children().stream().flatMap(child -> conjuncts(child).stream()).toList();
    }
    return List.of(filter);
  }

  @Override
  public FilterExpression resolveLocations(FilterExpression filter, User caller) {
    if (filter == null || !mentionsLocation(filter)) {
      return filter;
    }
    return rewrite(filter, caller, new HashMap<>());
  }

  @Override
  public ResourceRequest resolveLocations(ResourceRequest request, User caller) {
    FilterExpression filter = request.filter();
    FilterExpression resolved = resolveLocations(filter, caller);
    if (resolved == filter) {
      return request;
    }
    return new ResourceRequest(
        resolved,
        request.serverConstraint(),
        request.sort(),
        request.page(),
        request.fieldSelections(),
        request.includes(),
        request.runtime());
  }

  @Override
  public Map<Long, ApiV2BookingLocationResource.Location> findReadableLocations(
      Set<Long> ids, User caller) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, ApiV2BookingLocationResource.Location> locations = new LinkedHashMap<>();
    instruments
        .getBookingCatalogueLocations(null, Set.copyOf(ids), 1, ids.size(), caller)
        .resources()
        .forEach(summary -> locations.put(summary.containerId(), location(summary)));
    return locations;
  }

  /** The public view of one readable parent; a workbench keeps its {@code BE} global ID. */
  public static ApiV2BookingLocationResource.Location location(
      InstrumentParentLocationSummary summary) {
    GlobalIdPrefix prefix =
        summary.containerType() == Container.ContainerType.WORKBENCH
            ? GlobalIdPrefix.BE
            : GlobalIdPrefix.IC;
    return new ApiV2BookingLocationResource.Location(
        summary.containerId(), prefix.name() + summary.containerId(), summary.containerName());
  }

  private FilterExpression rewrite(
      FilterExpression filter, User caller, Map<Optional<Set<Long>>, Set<Long>> resolved) {
    if (filter instanceof FilterExpression.And and) {
      return new FilterExpression.And(
          and.children().stream().map(child -> rewrite(child, caller, resolved)).toList());
    }
    if (filter instanceof FilterExpression.Or or) {
      return new FilterExpression.Or(
          or.children().stream().map(child -> rewrite(child, caller, resolved)).toList());
    }
    FilterExpression.Comparison comparison = (FilterExpression.Comparison) filter;
    if (!ApiV2BookingLocationResource.isLocationSelector(comparison.field())) {
      return comparison;
    }
    Operator operator = comparison.operator();
    return switch (operator) {
      case EQUAL, IN ->
          targets(Operator.IN, items(Optional.of(containerIds(comparison)), caller, resolved));
      case NOT_EQUAL, NOT_IN ->
          targets(Operator.NOT_IN, items(Optional.of(containerIds(comparison)), caller, resolved));
      case EXISTS ->
          targets(
              Boolean.TRUE.equals(comparison.values().get(0)) ? Operator.IN : Operator.NOT_IN,
              items(Optional.empty(), caller, resolved));
      default -> throw new CollectionQueryException(CollectionQueryException.Reason.OPERATOR);
    };
  }

  private Set<Long> items(
      Optional<Set<Long>> parents, User caller, Map<Optional<Set<Long>>, Set<Long>> resolved) {
    return resolved.computeIfAbsent(
        parents,
        key -> {
          Set<Long> found =
              instruments.findConfiguredInstrumentIdsInReadableParents(key.orElse(null), caller);
          if (found.size() > MAX_LOCATION_ITEMS) {
            throw new CollectionQueryException(CollectionQueryException.Reason.COMPLEXITY);
          }
          return found;
        });
  }

  /**
   * The same target selector for both operators: the relationship compiler pairs it with the target
   * read rule, so neither form can match an item the caller cannot read.
   */
  private static FilterExpression targets(Operator operator, Set<Long> itemIds) {
    List<Object> values =
        itemIds.isEmpty() ? List.of(-1L) : List.copyOf(new TreeSet<Object>(itemIds));
    return new FilterExpression.Comparison("target.value", operator, values, false);
  }

  private static Set<Long> containerIds(FilterExpression.Comparison comparison) {
    return comparison.values().stream()
        .map(value -> value instanceof ResourceReference<?, ?> reference ? reference.id() : value)
        .map(value -> ((Number) value).longValue())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean mentionsLocation(FilterExpression filter) {
    if (filter instanceof FilterExpression.And and) {
      return and.children().stream().anyMatch(BookingLocationFilterManagerImpl::mentionsLocation);
    }
    if (filter instanceof FilterExpression.Or or) {
      return or.children().stream().anyMatch(BookingLocationFilterManagerImpl::mentionsLocation);
    }
    return ApiV2BookingLocationResource.isLocationSelector(
        ((FilterExpression.Comparison) filter).field());
  }
}
