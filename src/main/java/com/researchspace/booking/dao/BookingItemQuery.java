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
        user, edit, owner && (user == null || !user.hasSysadminRole()), targetPath);
  }

  /** Builds a restriction for targets owned by the caller, including for sysadmins. */
  public Predicate ownedBy(User user, String targetPath) {
    return buildRestriction(user, false, true, targetPath);
  }

  private Predicate buildRestriction(User user, boolean edit, boolean owner, String targetPath) {
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
                "capabilityInstrument",
                "bookingItemAccess")
            .translateTrusted(constraint);
    var parameters = new LinkedHashMap<String, Object>(access.parameters());
    parameters.put("bookingItemType", BookableTargetType.INSTRUMENT);
    String where =
        "capabilityInstrument.id = "
            + targetPath
            + ".id AND "
            + targetPath
            + ".type = :bookingItemType AND ("
            + access.expression()
            + ")";
    return new Predicate(
        "EXISTS bookingItemCapability",
        parameters,
        Map.of(
            "bookingItemCapability",
            new Subquery(Instrument.class, "capabilityInstrument", where, access.subqueries())));
  }

  /** Combines trusted restrictions without changing their parameter or subquery bindings. */
  public static Predicate and(Predicate left, Predicate right) {
    if (left == null) return right;
    if (right == null) return left;
    var parameters = new LinkedHashMap<String, Object>(left.parameters());
    parameters.putAll(right.parameters());
    var subqueries = new LinkedHashMap<String, Subquery>(left.subqueries());
    subqueries.putAll(right.subqueries());
    return new Predicate(
        "(" + left.expression() + ") AND (" + right.expression() + ")", parameters, subqueries);
  }
}
