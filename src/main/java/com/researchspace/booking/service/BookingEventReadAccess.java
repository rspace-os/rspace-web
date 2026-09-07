package com.researchspace.booking.service;

import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessDocumentation;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.service.resourceaccess.ResourceRoleReadAccess;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Database-enforceable access for Booking event rows.
 *
 * <p>A current configuration role reveals every event on that configuration. Independently, a
 * requester may continue to read their own booking after losing every configuration role. The two
 * branches are one trusted query predicate so list totals and pagination are authorization-safe.
 */
public final class BookingEventReadAccess implements AccessFunction {

  private final ResourceRoleReadAccess configurationAccess;

  public BookingEventReadAccess(ResourceRoleSchemeRegistry schemes) {
    configurationAccess =
        new ResourceRoleReadAccess(
            schemes,
            BookingResourceRoleScheme.SCHEME_KEY,
            "bookingConfiguration.resourceAccess.id");
  }

  @Override
  public AccessResult check(AccessContext context) {
    AccessResult configurationResult = configurationAccess.check(context);
    if (configurationResult.isDenied() || configurationResult instanceof AccessResult.Allowed) {
      return configurationResult;
    }
    QueryConstraint membership = configurationResult.constraintOrEmpty().orElseThrow();
    QueryConstraint requestedByCaller =
        new FilterExpression.Comparison(
            "requesterId", Operator.EQUAL, List.of(context.user().getId()), false);
    return AccessResult.allowedWhere(
        new QueryConstraint.Or(List.of(membership, requestedByCaller)));
  }

  @Override
  public Optional<AccessDocumentation> documentation() {
    return Optional.of(
        new AccessDocumentation(
            "A logged-in user may read events for a currently readable Booking configuration and"
                + " may retain read-only access to their own bookings.",
            Set.of(AccessPolicy.AUTHENTICATION_REQUIRED)));
  }
}
