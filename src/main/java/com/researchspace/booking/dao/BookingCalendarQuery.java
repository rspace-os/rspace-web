package com.researchspace.booking.dao;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetType;
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
import java.time.Instant;
import java.util.ArrayList;
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
            .translate(calendarFilter(eventRequest.filter()), targets, eventRequest.runtime());
    ResourceRequest scoped = eventRequest.restrict(interval(start, end));
    if (access.constraintOrEmpty().isPresent())
      scoped = scoped.restrict(access.constraintOrEmpty().orElseThrow());
    Predicate mandatory =
        new RsqlCollectionQuery(events, alias, "calendarAccess")
            .translateTrusted(scoped.serverConstraint(), targets);
    Predicate itemText = itemText("bookingConfiguration.target", text, caller);
    Predicate eventTextPredicate = eventText(alias, text);
    Predicate body =
        and(
            new Predicate(alias + ".bookingConfiguration.id = bookingConfiguration.id", Map.of()),
            mandatory,
            filter,
            filtered ? or(itemText, eventTextPredicate) : eventTextPredicate);
    Predicate exists =
        new Predicate(
            "EXISTS calendarEvents",
            body.parameters(),
            Map.of(
                "calendarEvents",
                new Subquery(TimeSlotBooking.class, alias, body.expression(), body.subqueries())));
    return filtered ? exists : or(itemText, exists);
  }

  /** Search does not alter the event's mandatory visibility, interval or explicit filters. */
  public Predicate eventSearch(String text, User caller) {
    return or(
        itemText("booking.bookingConfiguration.target", text, caller), eventText("booking", text));
  }

  /** Compiles the Calendar-only event facets without publishing them on the bookings collection. */
  public Predicate eventFilter(ResourceRequest request, String text, User caller) {
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry.getObject(), caller);
    Predicate filter =
        new RsqlCollectionQuery(calendarEvents, "booking", "calendarFilter")
            .translate(calendarFilter(request.filter()), targets, request.runtime());
    return and(filter, eventSearch(text, caller));
  }

  private static FilterExpression calendarFilter(FilterExpression filter) {
    if (filter == null) return null;
    if (filter instanceof FilterExpression.And and) {
      return new FilterExpression.And(
          and.children().stream().map(BookingCalendarQuery::calendarFilter).toList());
    }
    if (filter instanceof FilterExpression.Or or) {
      return new FilterExpression.Or(
          or.children().stream().map(BookingCalendarQuery::calendarFilter).toList());
    }
    FilterExpression.Comparison comparison = (FilterExpression.Comparison) filter;
    if (comparison.field().equals("privacy")) {
      boolean matches = privacyMatches(comparison);
      return new FilterExpression.Comparison("privacy", Operator.EXISTS, List.of(matches), false);
    }
    if (comparison.field().equals("timezone")) {
      return new FilterExpression.And(List.of(comparison, readableTarget()));
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

  private static FilterExpression readableTarget() {
    return new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false);
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

  private Predicate itemText(String targetPath, String text, User caller) {
    if (text == null || text.isBlank()) return null;
    RelationshipReadAccess targets = RelationshipReadAccess.forActor(registry.getObject(), caller);
    CollectionDescription<?> instruments = targets.description("instruments");
    AccessResult access = targets.result("instruments");
    Predicate descriptionAccess = new Predicate("1 = 0", Map.of());
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
    Map<String, Object> parameters = new LinkedHashMap<>(descriptionAccess.parameters());
    parameters.put("calendarText", pattern(text));
    parameters.put("calendarTargetType", BookableTargetType.INSTRUMENT);
    String exactId = "";
    if (text.trim().toUpperCase(Locale.ROOT).matches("IN[1-9][0-9]*")) {
      java.math.BigInteger id = new java.math.BigInteger(text.trim().substring(2));
      if (id.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
        parameters.put("calendarTargetId", id.longValue());
        exactId = " OR calendarInstrument.id = :calendarTargetId";
      }
    }
    String where =
        "calendarInstrument.id = "
            + targetPath
            + ".id AND "
            + targetPath
            + ".type = :calendarTargetType AND calendarInstrument.deleted = false AND ("
            + descriptionAccess.expression()
            + ") AND (LOWER(calendarInstrument.editInfo.name) LIKE :calendarText ESCAPE '\\'"
            + exactId
            + " OR LOWER(calendarInstrument.editInfo.description) LIKE :calendarText ESCAPE '\\')";
    return new Predicate(
        "EXISTS calendarItemText",
        parameters,
        Map.of(
            "calendarItemText",
            new Subquery(
                Instrument.class, "calendarInstrument", where, descriptionAccess.subqueries())));
  }

  private static Predicate purpose(String alias, String text) {
    return text == null || text.isBlank()
        ? null
        : new Predicate(
            "LOWER(" + alias + ".purpose) LIKE :calendarText ESCAPE '\\'",
            Map.of("calendarText", pattern(text)));
  }

  private static Predicate eventText(String alias, String text) {
    if (text == null || text.isBlank()) return null;
    return or(
        purpose(alias, text),
        new Predicate(
            "LOWER("
                + alias
                + ".requester.username) LIKE :calendarText ESCAPE '\\'"
                + " OR LOWER("
                + alias
                + ".requester.firstName) LIKE :calendarText ESCAPE '\\'"
                + " OR LOWER("
                + alias
                + ".requester.lastName) LIKE :calendarText ESCAPE '\\'"
                + " OR LOWER(CONCAT("
                + alias
                + ".requester.firstName, ' ', "
                + alias
                + ".requester.lastName)) LIKE :calendarText ESCAPE '\\'",
            Map.of("calendarText", pattern(text))));
  }

  private static String pattern(String value) {
    return "%"
        + value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }

  private static FilterExpression comparison(String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }

  private static Predicate and(Predicate... predicates) {
    return combine(" AND ", predicates);
  }

  private static Predicate or(Predicate... predicates) {
    return combine(" OR ", predicates);
  }

  private static Predicate combine(String operator, Predicate... predicates) {
    List<String> expressions = new ArrayList<>();
    Map<String, Object> parameters = new LinkedHashMap<>();
    Map<String, Subquery> subqueries = new LinkedHashMap<>();
    for (Predicate predicate : predicates) {
      if (predicate == null) continue;
      expressions.add("(" + predicate.expression() + ")");
      parameters.putAll(predicate.parameters());
      subqueries.putAll(predicate.subqueries());
    }
    return expressions.isEmpty()
        ? null
        : new Predicate(String.join(operator, expressions), parameters, subqueries);
  }
}
