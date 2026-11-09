package com.researchspace.booking.dao;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.dao.query.RsqlCollectionQuery.Subquery;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentReadAccess;
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
        user, edit, owner && (user == null || !user.hasSysadminRole()), targetPath, "bookingItem");
  }

  /** Builds a restriction for targets owned by the caller, including for sysadmins. */
  public Predicate ownedBy(User user, String targetPath) {
    return buildRestriction(user, false, true, targetPath, "ownedBookingItem");
  }

  /** Compiles a parsed owner preset against the registered Inventory read constraint. */
  public static Predicate ownedBy(User user, String targetPath, RelationshipReadAccess targets) {
    if (user == null
        || !user.isEnabled()
        || user.isAccountLocked()
        || targets.description("instruments") == null
        || targets.result("instruments").isDenied()) {
      return new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of());
    }
    String name = targetPath.replace('.', '_') + "Owner";
    String instrument = name + "Instrument";
    var constraint = targets.result("instruments").constraintOrEmpty().orElse(null);
    Predicate access =
        new RsqlCollectionQuery(targets.description("instruments"), instrument, name)
            .translateTrusted(constraint, targets);
    Predicate body =
        Predicate.and(
            access,
            new Predicate(
                instrument
                    + ".id = "
                    + targetPath
                    + ".id AND "
                    + targetPath
                    + ".type = :"
                    + name
                    + "Type AND "
                    + instrument
                    + ".owner.username = :"
                    + name
                    + "Username",
                Map.of(
                    name + "Type",
                    BookableTargetType.INSTRUMENT,
                    name + "Username",
                    user.getUsername())));
    return new Predicate(
        "EXISTS " + name + "Capability",
        body.parameters(),
        Map.of(
            name + "Capability",
            new Subquery(Instrument.class, instrument, body.expression(), body.subqueries())));
  }

  /** Default list scope without changing configuration or Inventory authorization. */
  public static Predicate undeletedTarget(String targetPath) {
    String name = "undeletedBookingTarget";
    return new Predicate(
        "EXISTS " + name,
        Map.of(),
        Map.of(
            name,
            new Subquery(
                Instrument.class,
                name,
                name + ".id = " + targetPath + ".id AND " + name + ".deleted = false")));
  }

  private Predicate buildRestriction(
      User user, boolean edit, boolean owner, String targetPath, String name) {
    if (user == null || !user.isEnabled() || user.isAccountLocked()) {
      return new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of());
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
                ApiV2InstrumentResource.description(inventory),
                name + "Instrument",
                name + "Access")
            .translateTrusted(constraint);
    Predicate body =
        Predicate.and(
            new Predicate(
                name
                    + "Instrument.id = "
                    + targetPath
                    + ".id AND "
                    + targetPath
                    + ".type = :"
                    + name
                    + "Type",
                Map.of(name + "Type", BookableTargetType.INSTRUMENT)),
            access);
    return new Predicate(
        "EXISTS " + name + "Capability",
        body.parameters(),
        Map.of(
            name + "Capability",
            new Subquery(
                Instrument.class, name + "Instrument", body.expression(), body.subqueries())));
  }
}
