package com.researchspace.booking.dao;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Compiles Inventory capability restrictions before Booking paging and counting. */
@Component
public class BookingItemQuery {
  private final InstrumentReadAccess inventory;

  public BookingItemQuery(InstrumentReadAccess inventory) {
    this.inventory = inventory;
  }

  /** Builds a correlated item restriction for a trusted configuration target path. */
  public Predicate restriction(User user, boolean edit, boolean owner, String targetPath) {
    return buildRestriction(
        user, edit, owner && (user == null || !user.hasSysadminRole()), targetPath, "bookingItem");
  }

  /** Builds a restriction for targets owned by the caller, including for sysadmins. */
  public Predicate ownedBy(User user, String targetPath) {
    return buildRestriction(user, false, true, targetPath, "ownedBookingItem");
  }

  private Predicate buildRestriction(
      User user, boolean edit, boolean owner, String targetPath, String name) {
    if (user == null || !user.isEnabled() || user.isAccountLocked()) {
      return new Predicate("1 = 0", Map.of());
    }
    FilterExpression constraint = inventory.constraint(user, edit);
    if (owner) {
      constraint =
          new FilterExpression.And(
              List.of(
                  constraint,
                  new FilterExpression.Comparison(
                      "ownerUsername", Operator.EQUAL, List.of(user.getUsername()), false)));
    }
    Predicate access =
        new RsqlCollectionQuery(
                ApiV2BookingInstrumentResource.description(inventory),
                name + "Instrument",
                name + "Access")
            .translateTrusted(constraint);
    var parameters = new LinkedHashMap<String, Object>(access.parameters());
    parameters.put(name + "Type", BookableTargetType.INSTRUMENT);
    String where =
        name
            + "Instrument.id = "
            + targetPath
            + ".id AND "
            + targetPath
            + ".type = :"
            + name
            + "Type AND ("
            + access.expression()
            + ")";
    return new Predicate(
        "EXISTS " + name + "Capability",
        parameters,
        Map.of(
            name + "Capability",
            new Subquery(Instrument.class, name + "Instrument", where, access.subqueries())));
  }

  /** Combines trusted restrictions without changing their parameter or subquery bindings. */
  public static Predicate and(Predicate left, Predicate right) {
    if (left == null) return right;
    if (right == null) return left;
    var parameters = new LinkedHashMap<String, Object>(left.parameters());
    mergeWithoutConflicts(parameters, right.parameters(), "parameter");
    var subqueries = new LinkedHashMap<String, Subquery>(left.subqueries());
    mergeWithoutConflicts(subqueries, right.subqueries(), "subquery");
    return new Predicate(
        "(" + left.expression() + ") AND (" + right.expression() + ")", parameters, subqueries);
  }

  private static <T> void mergeWithoutConflicts(
      Map<String, T> destination, Map<String, T> incoming, String bindingType) {
    incoming.forEach(
        (name, value) -> {
          if (destination.containsKey(name) && !Objects.equals(destination.get(name), value)) {
            throw new IllegalArgumentException(
                "Trusted restriction " + bindingType + " name collides: " + name);
          }
          destination.putIfAbsent(name, value);
        });
  }
}
