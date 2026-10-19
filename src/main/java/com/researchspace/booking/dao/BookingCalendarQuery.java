package com.researchspace.booking.dao;

import com.researchspace.dao.query.LikeEscaper;
import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.dao.query.RsqlSqlFragments;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingRequesterFilters;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Correlated Calendar predicates shared by resource paging and event paging. */
@Component
public final class BookingCalendarQuery {
  private final CollectionDescription<TimeSlotBooking> events;
  private final CollectionDescription<TimeSlotBooking> calendarEvents;
  private final ObjectProvider<ResourceRegistry> registry;

  public BookingCalendarQuery(
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .TIME_SLOT_BOOKING_DESCRIPTION)
          CollectionDescription<TimeSlotBooking> events,
      ObjectProvider<ResourceRegistry> registry) {
    this.events = events;
    this.calendarEvents =
        ApiV2TimeSlotBookingResource.calendarFilterDescription(events.accessPolicy().readAccess());
    this.registry = registry;
  }

  /** Requires one visible event to satisfy the complete expression, before count and pagination. */
  public Predicate resources(
      ResourceRequest eventRequest, Instant start, Instant end, String text, User caller) {
    boolean filtered = eventRequest.filter() != null;
    if (!filtered && (text == null || text.isBlank())) return null;
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry.getObject(), caller);
    AccessResult access =
        events
            .accessPolicy()
            .readAccess()
            .check(new AccessContext(caller, AccessContext.Operation.READ, events.resourceName()));
    if (access.isDenied()) throw new AuthorizationException("errors.api.v2.authenticationRequired");
    String alias = "calendarEvent";
    Predicate filter =
        new RsqlCollectionQuery(calendarEvents, alias, "calendarFilter")
            .translate(
                calendarFilter(eventRequest.filter(), caller), targets, eventRequest.runtime());
    ResourceRequest scoped = eventRequest.restrict(interval(start, end));
    if (access.constraintOrEmpty().isPresent())
      scoped = scoped.restrict(access.constraintOrEmpty().orElseThrow());
    Predicate mandatory =
        new RsqlCollectionQuery(events, alias, "calendarAccess")
            .translateTrusted(scoped.serverConstraint(), targets);
    Predicate itemText = itemText("bookingConfiguration.target", text, caller, targets);
    Predicate eventTextPredicate = eventText(alias, text, caller, targets);
    Predicate body =
        Predicate.and(
            new Predicate(alias + ".bookingConfiguration.id = bookingConfiguration.id", Map.of()),
            mandatory,
            filter,
            filtered ? Predicate.or(itemText, eventTextPredicate) : eventTextPredicate);
    Predicate exists =
        new Predicate(
            "EXISTS calendarEvents",
            body.parameters(),
            Map.of(
                "calendarEvents",
                new Subquery(TimeSlotBooking.class, alias, body.expression(), body.subqueries())),
            body.callerLikePredicateCount());
    return filtered ? exists : Predicate.or(itemText, exists);
  }

  /** Search does not alter the event's mandatory visibility, interval or explicit filters. */
  public Predicate eventSearch(String text, User caller) {
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry.getObject(), caller);
    return Predicate.or(
        itemText("booking.bookingConfiguration.target", text, caller, targets),
        eventText("booking", text, caller, targets));
  }

  /** Compiles the Calendar-only event facets without publishing them on the bookings collection. */
  public Predicate eventFilter(ResourceRequest request, String text, User caller) {
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry.getObject(), caller);
    Predicate filter =
        new RsqlCollectionQuery(calendarEvents, "booking", "calendarFilter")
            .translate(calendarFilter(request.filter(), caller), targets, request.runtime());
    return Predicate.and(filter, eventSearch(text, caller));
  }

  /**
   * Maps the Calendar facets onto stored selectors, then limits every requester comparison,
   * including the {@code bookedBy} name facet, to events whose requester the caller may see.
   */
  private static FilterExpression calendarFilter(FilterExpression filter, User caller) {
    return BookingRequesterFilters.visibleRequestersOnly(
        calendarFacets(filter), caller == null ? null : caller.getId());
  }

  private static FilterExpression calendarFacets(FilterExpression filter) {
    return BookingRequesterFilters.mapComparisons(filter, BookingCalendarQuery::calendarFacet);
  }

  private static FilterExpression calendarFacet(FilterExpression.Comparison comparison) {
    if (comparison.field().equals("privacy")) {
      boolean matches = privacyMatches(comparison);
      return new FilterExpression.Comparison("privacy", Operator.EXISTS, List.of(matches), false);
    }
    if (comparison.field().equals("timezone")) {
      return new FilterExpression.And(List.of(comparison, BookingRequesterFilters.READABLE_ITEM));
    }
    if (!comparison.field().equals("bookedBy")) return comparison;
    if (comparison.operator() == Operator.EXISTS) {
      return new FilterExpression.Comparison(
          "privacy", Operator.EXISTS, comparison.values(), false);
    }
    String value = String.valueOf(comparison.values().get(0));
    List<String> words = List.of(value.replace('(', ' ').replace(')', ' ').trim().split("\\s+"));
    if (words.size() > 1) {
      return new FilterExpression.And(
          words.stream().map(word -> bookedBy(comparison, word)).toList());
    }
    return bookedBy(comparison, value);
  }

  private static FilterExpression bookedBy(FilterExpression.Comparison comparison, String value) {
    List<FilterExpression> names =
        List.of("requesterUsername", "requesterFirstName", "requesterLastName").stream()
            .map(
                field ->
                    (FilterExpression)
                        new FilterExpression.Comparison(
                            field, comparison.operator(), List.of(value), comparison.wildcard()))
            .toList();
    return new FilterExpression.Or(names);
  }

  private static boolean privacyMatches(FilterExpression.Comparison comparison) {
    boolean containsFull =
        comparison.values().stream()
            .map(String::valueOf)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch("full"::equals);
    return switch (comparison.operator()) {
      case EQUAL, IN -> containsFull;
      case NOT_EQUAL, NOT_IN -> !containsFull;
      case EXISTS -> Boolean.TRUE.equals(comparison.values().get(0));
      default -> false;
    };
  }

  /** Mandatory half-open overlap, state and soft-deletion scope. */
  public static QueryConstraint interval(Instant start, Instant end) {
    return new FilterExpression.And(
        List.of(
            comparison("start", Operator.LESS_THAN, Date.from(end)),
                comparison("end", Operator.GREATER_THAN, Date.from(start)),
            comparison("state", Operator.EQUAL, BookingState.CONFIRMED),
                comparison("deleted", Operator.EQUAL, false)));
  }

  private Predicate itemText(
      String targetPath, String text, User caller, RelationshipReadAccess targets) {
    if (text == null || text.isBlank()) return null;
    CollectionDescription<?> instruments = targets.description("instruments");
    AccessResult access = targets.result("instruments");
    Predicate descriptionAccess = new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of());
    if (instruments != null && !access.isDenied()) {
      descriptionAccess =
          access
              .constraintOrEmpty()
              .map(
                  constraint ->
                      new RsqlCollectionQuery(
                              instruments, "calendarInstrument", "calendarInventoryAccess")
                          .translateTrusted(constraint, targets))
              .orElse(new Predicate("1 = 1", Map.of()));
    }
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("calendarItemTextPattern", pattern(text));
    String exactId = "";
    if (text.trim().toUpperCase(Locale.ROOT).matches("IN[1-9][0-9]*")) {
      BigInteger id = new BigInteger(text.trim().substring(2));
      if (id.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
        parameters.put("calendarTargetId", id.longValue());
        exactId = " OR calendarInstrument.id = :calendarTargetId";
      }
    }
    Predicate body =
        Predicate.and(
            new Predicate(
                "calendarInstrument.id = "
                    + targetPath
                    + ".id AND "
                    + targetPath
                    + ".type = :calendarTargetType AND calendarInstrument.deleted = false",
                Map.of("calendarTargetType", BookableTargetType.INSTRUMENT)),
            descriptionAccess,
            new Predicate(
                RsqlSqlFragments.like(
                        "calendarInstrument.editInfo.name", "calendarItemTextPattern", false)
                    + exactId
                    + " OR "
                    + RsqlSqlFragments.like(
                        "calendarInstrument.editInfo.description",
                        "calendarItemTextPattern",
                        false),
                parameters));
    return new Predicate(
        "EXISTS calendarItemText",
        body.parameters(),
        Map.of(
            "calendarItemText",
            new Subquery(
                Instrument.class, "calendarInstrument", body.expression(), body.subqueries())));
  }

  private static Predicate purpose(String alias, String text) {
    return text == null || text.isBlank()
        ? null
        : new Predicate(
            RsqlSqlFragments.like(alias + ".purpose", "calendarEventTextPattern", false),
            Map.of("calendarEventTextPattern", pattern(text)));
  }

  /**
   * Matches the purpose or requester name only on events that show them, so Search cannot reveal
   * either for an event the caller sees as busy.
   */
  private Predicate eventText(
      String alias, String text, User caller, RelationshipReadAccess targets) {
    if (text == null || text.isBlank()) return null;
    Predicate shown =
        new RsqlCollectionQuery(calendarEvents, alias, alias + "TextShown")
            .translateTrusted(
                BookingRequesterFilters.requesterVisible(caller == null ? null : caller.getId()),
                targets);
    return Predicate.and(shown, eventTextMatch(alias, text));
  }

  private static Predicate eventTextMatch(String alias, String text) {
    return Predicate.or(
        purpose(alias, text),
        new Predicate(
            RsqlSqlFragments.like(alias + ".requester.username", "calendarEventTextPattern", false)
                + " OR "
                + RsqlSqlFragments.like(
                    alias + ".requester.firstName", "calendarEventTextPattern", false)
                + " OR "
                + RsqlSqlFragments.like(
                    alias + ".requester.lastName", "calendarEventTextPattern", false)
                + " OR "
                + RsqlSqlFragments.like(
                    "CONCAT("
                        + alias
                        + ".requester.firstName, ' ', "
                        + alias
                        + ".requester.lastName)",
                    "calendarEventTextPattern",
                    false),
            Map.of("calendarEventTextPattern", pattern(text))));
  }

  private static String pattern(String value) {
    return "%" + LikeEscaper.escape(value.trim()) + "%";
  }

  private static FilterExpression comparison(String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }
}
