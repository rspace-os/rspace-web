package com.researchspace.booking.service;

import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Database-enforceable access for Booking event rows.
 *
 * <p>A caller with current read access to the target may read every event on that target.
 * Independently, a requester may continue to read their own booking after losing target access. The
 * two branches form one trusted query predicate, keeping list totals and pagination
 * authorization-safe.
 */
public final class BookingEventReadAccess implements AccessFunction {

  public BookingEventReadAccess() {}

  /** Keeps the configuration bean source-compatible while access comes from the target policy. */
  public BookingEventReadAccess(ResourceRoleSchemeRegistry ignored) {
    this();
  }

  @Override
  public AccessResult check(AccessContext context) {
    if (!context.isAuthenticated()
        || context.user() == null
        || !context.user().isEnabled()
        || context.user().isAccountLocked()
        || context.user().getId() == null) {
      return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
    }
    FilterExpression targetReadable =
        new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false);
    QueryConstraint requestedByCaller =
        new FilterExpression.Comparison(
            "requesterId", Operator.EQUAL, List.of(context.user().getId()), false);
    return AccessResult.allowedWhere(
        new QueryConstraint.Or(List.of(targetReadable, requestedByCaller)));
  }

  @Override
  public Optional<AccessDocumentation> documentation() {
    return Optional.of(
        new AccessDocumentation(
            "A logged-in user may read events for a currently readable Inventory item and may "
                + "retain read-only access to their own bookings.",
            Set.of(AccessPolicy.AUTHENTICATION_REQUIRED)));
  }
}
