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
    if (user == null || !user.isEnabled() || user.isAccountLocked()) {
      return new Predicate(RsqlCollectionQuery.FALSE_EXPRESSION, Map.of());
    }
    FilterExpression constraint = inventory.constraint(user, edit);
    if (owner && !user.hasSysadminRole()) {
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
    Predicate body =
        Predicate.and(
            new Predicate(
                "capabilityInstrument.id = "
                    + targetPath
                    + ".id AND "
                    + targetPath
                    + ".type = :bookingItemType",
                Map.of("bookingItemType", BookableTargetType.INSTRUMENT)),
            access);
    return new Predicate(
        "EXISTS bookingItemCapability",
        body.parameters(),
        Map.of(
            "bookingItemCapability",
            new Subquery(
                Instrument.class, "capabilityInstrument", body.expression(), body.subqueries())));
  }
}
