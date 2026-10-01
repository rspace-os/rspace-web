package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.QueryConstraint;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingEventReadAccessTest {

  private final BookingEventReadAccess access =
      new BookingEventReadAccess(
          new ResourceRoleSchemeRegistry(List.of(new BookingResourceRoleScheme())));
  private final User subject = mock(User.class);

  @BeforeEach
  void setUp() {
    when(subject.getId()).thenReturn(12L);
    when(subject.isEnabled()).thenReturn(true);
    when(subject.getGroups()).thenReturn(Set.of());
  }

  @Test
  void deniesAnonymousCallers() {
    assertTrue(access.check(new AccessContext(null, Operation.READ, "bookings")).isDenied());
  }

  @Test
  void combinesCurrentRoleAndOwnBookingBranches() {
    AccessResult.AllowedWhere result =
        assertInstanceOf(
            AccessResult.AllowedWhere.class,
            access.check(new AccessContext(subject, Operation.READ, "bookings")));
    QueryConstraint.Or disjunction =
        assertInstanceOf(QueryConstraint.Or.class, result.constraint());

    assertInstanceOf(ResourceRoleMembershipConstraint.class, disjunction.children().get(0));
    FilterExpression.Comparison own =
        assertInstanceOf(FilterExpression.Comparison.class, disjunction.children().get(1));
    assertEquals("requesterId", own.field());
    assertEquals(List.of(12L), own.values());
  }

  @Test
  void implicitSysadminRoleNeedsNoOwnBookingDisjunction() {
    when(subject.hasSysadminRole()).thenReturn(true);

    assertInstanceOf(
        AccessResult.Allowed.class,
        access.check(new AccessContext(subject, Operation.READ, "bookings")));
  }
}
