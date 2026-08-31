package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BookingResourceRoleSchemeTest {

  private final BookingResourceRoleScheme scheme = new BookingResourceRoleScheme();

  @Test
  void capabilityMatrixIsMonotonicAndMatchesBookingOperations() {
    scheme.validate();

    assertEquals(
        Set.of(
            BookingResourceRoleScheme.READ_RESOURCE,
            BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION),
        scheme.capabilities(BookingResourceRoleScheme.VIEWER));
    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.BOOKER)
            .containsAll(scheme.capabilities(BookingResourceRoleScheme.VIEWER)));
    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.MANAGER)
            .containsAll(scheme.capabilities(BookingResourceRoleScheme.BOOKER)));
    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.OWNER)
            .containsAll(scheme.capabilities(BookingResourceRoleScheme.MANAGER)));

    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.BOOKER)
            .contains(BookingResourceRoleScheme.CREATE_BOOKING));
    assertFalse(
        scheme
            .capabilities(BookingResourceRoleScheme.VIEWER)
            .contains(BookingResourceRoleScheme.CREATE_BOOKING));
    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.MANAGER)
            .contains(BookingResourceRoleScheme.MANAGE_ALL_EVENTS));
    assertFalse(
        scheme
            .capabilities(BookingResourceRoleScheme.MANAGER)
            .contains(BookingResourceRoleScheme.MANAGE_OWNERS));
    assertTrue(
        scheme
            .capabilities(BookingResourceRoleScheme.OWNER)
            .contains(BookingResourceRoleScheme.ARCHIVE_CONFIGURATION));
  }

  @Test
  void allUsersAudienceIsLimitedToBookerAndViewer() {
    assertEquals(
        Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP),
        scheme.allowedGranteeKinds(BookingResourceRoleScheme.OWNER));
    assertEquals(
        Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP),
        scheme.allowedGranteeKinds(BookingResourceRoleScheme.MANAGER));
    assertTrue(
        scheme
            .allowedGranteeKinds(BookingResourceRoleScheme.BOOKER)
            .contains(ResourceGranteeKind.AUDIENCE));
    assertTrue(
        scheme
            .allowedGranteeKinds(BookingResourceRoleScheme.VIEWER)
            .contains(ResourceGranteeKind.AUDIENCE));
  }

  @Test
  void onlyRepresentedSysadminReceivesImplicitOwner() {
    User sysadmin = mock(User.class);
    when(sysadmin.hasSysadminRole()).thenReturn(true);
    User ordinaryUser = mock(User.class);

    assertEquals(BookingResourceRoleScheme.OWNER, scheme.implicitRole(sysadmin).orElseThrow());
    assertTrue(scheme.implicitRole(ordinaryUser).isEmpty());
  }
}
