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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingEventReadAccessTest {

  private final BookingEventReadAccess access = new BookingEventReadAccess();
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
  void combinesCurrentTargetReadAndOwnBookingBranches() {
    AccessResult.AllowedWhere result =
        assertInstanceOf(
            AccessResult.AllowedWhere.class,
            access.check(new AccessContext(subject, Operation.READ, "bookings")));
    QueryConstraint.Or disjunction =
        assertInstanceOf(QueryConstraint.Or.class, result.constraint());

    FilterExpression.Comparison target =
        assertInstanceOf(FilterExpression.Comparison.class, disjunction.children().get(0));
    assertEquals("target", target.field());
    assertEquals(com.researchspace.model.collection.Operator.EXISTS, target.operator());
    FilterExpression.Comparison own =
        assertInstanceOf(FilterExpression.Comparison.class, disjunction.children().get(1));
    assertEquals("requesterId", own.field());
    assertEquals(List.of(12L), own.values());
  }

  @Test
  void sysadminStillUsesCurrentTargetReadPredicate() {
    when(subject.hasSysadminRole()).thenReturn(true);

    assertInstanceOf(
        AccessResult.AllowedWhere.class,
        access.check(new AccessContext(subject, Operation.READ, "bookings")));
  }
}
