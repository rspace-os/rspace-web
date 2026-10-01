package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceRoleReadAccessTest {

  private final BookingResourceRoleScheme scheme = new BookingResourceRoleScheme();
  private final ResourceRoleReadAccess access =
      new ResourceRoleReadAccess(
          new ResourceRoleSchemeRegistry(List.of(scheme)), scheme.key(), "resourceAccess.id");
  private User subject;

  @BeforeEach
  void setUp() {
    subject = mock(User.class);
    when(subject.getId()).thenReturn(12L);
    when(subject.isEnabled()).thenReturn(true);
    when(subject.getGroups()).thenReturn(Set.of());
  }

  @Test
  void requiresAuthentication() {
    assertTrue(
        access.check(new AccessContext(null, Operation.READ, "booking-configurations")).isDenied());
  }

  @Test
  void returnsTrustedMembershipConstraintForOrdinarySubject() {
    Group active = mock(Group.class);
    when(active.getId()).thenReturn(41L);
    when(active.getEnabledMemberSize()).thenReturn(2);
    Group empty = mock(Group.class);
    when(empty.getId()).thenReturn(42L);
    when(empty.getEnabledMemberSize()).thenReturn(0);
    when(subject.getGroups()).thenReturn(Set.of(active, empty));

    AccessResult.AllowedWhere result =
        assertInstanceOf(
            AccessResult.AllowedWhere.class,
            access.check(new AccessContext(subject, Operation.READ, "booking-configurations")));
    ResourceRoleMembershipConstraint constraint =
        assertInstanceOf(ResourceRoleMembershipConstraint.class, result.constraint());

    assertEquals(Set.of(41L), constraint.currentGroupIds());
    assertEquals(
        Set.of(
            BookingResourceRoleScheme.OWNER,
            BookingResourceRoleScheme.MANAGER,
            BookingResourceRoleScheme.BOOKER,
            BookingResourceRoleScheme.VIEWER),
        constraint.readableRoleKeys());
    assertTrue(constraint.includeAllUsers());
  }

  @Test
  void supportsNonZeroSeedUserIds() {
    when(subject.getId()).thenReturn(-1L);

    AccessResult.AllowedWhere result =
        assertInstanceOf(
            AccessResult.AllowedWhere.class,
            access.check(new AccessContext(subject, Operation.READ, "booking-configurations")));
    ResourceRoleMembershipConstraint constraint =
        assertInstanceOf(ResourceRoleMembershipConstraint.class, result.constraint());

    assertEquals(-1L, constraint.subjectId());
  }

  @Test
  void implicitReadableRoleNeedsNoRowConstraint() {
    when(subject.hasSysadminRole()).thenReturn(true);

    assertInstanceOf(
        AccessResult.Allowed.class,
        access.check(new AccessContext(subject, Operation.READ, "booking-configurations")));
  }
}
