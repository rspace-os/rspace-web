package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingItemPermissionsTest {

  private static final long TARGET_ID = 17L;

  @Mock private InventoryPermissionUtils inventoryPermissions;
  @Mock private InstrumentDao instruments;
  @Mock private User subject;
  @Mock private User itemOwner;
  @Mock private Instrument instrument;

  private BookingItemPermissions permissions;
  private BookingConfiguration configuration;

  @BeforeEach
  void setUp() {
    permissions =
        new BookingItemPermissions(
            inventoryPermissions, instruments, new BookingResourceRoleScheme());
    configuration = new BookingConfiguration();
    configuration.setId(23L);
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, TARGET_ID));

    when(subject.isEnabled()).thenReturn(true);
    when(instrument.getOwner()).thenReturn(itemOwner);
    when(inventoryPermissions.canUserReadInventoryRecord(instrument, subject)).thenReturn(true);
  }

  @Test
  void mapsTheItemOwnerToBookingOwnerWithoutAclManagement() {
    when(subject.getUsername()).thenReturn("owner");
    when(itemOwner.getUsername()).thenReturn("owner");

    when(instruments.getSafeNull(TARGET_ID)).thenReturn(Optional.of(instrument));
    ResolvedResourceAccess resolved = permissions.resolve(configuration, subject);

    assertEquals(Optional.of(BookingResourceRoleScheme.OWNER), resolved.effectiveRole());
    assertTrue(resolved.hasCapability(BookingResourceRoleScheme.EDIT_CONFIGURATION));
    assertTrue(resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS));
    assertFalse(resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS));
    assertFalse(resolved.hasCapability(BookingResourceRoleScheme.MANAGE_OWNERS));
  }

  @Test
  void mapsAnInventoryEditorToBooker() {
    when(subject.getUsername()).thenReturn("booker");
    when(itemOwner.getUsername()).thenReturn("owner");
    when(inventoryPermissions.canUserEditInventoryRecord(instrument, subject)).thenReturn(true);

    when(instruments.getSafeNull(TARGET_ID)).thenReturn(Optional.of(instrument));
    ResolvedResourceAccess resolved = permissions.resolve(configuration, subject);

    assertEquals(Optional.of(BookingResourceRoleScheme.BOOKER), resolved.effectiveRole());
    assertTrue(resolved.hasCapability(BookingResourceRoleScheme.CREATE_BOOKING));
    assertFalse(resolved.hasCapability(BookingResourceRoleScheme.EDIT_CONFIGURATION));
    assertFalse(resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS));
  }

  @Test
  void mapsAnInventoryReaderToViewer() {
    when(subject.getUsername()).thenReturn("viewer");
    when(itemOwner.getUsername()).thenReturn("owner");

    when(instruments.getSafeNull(TARGET_ID)).thenReturn(Optional.of(instrument));
    ResolvedResourceAccess resolved = permissions.resolve(configuration, subject);

    assertEquals(Optional.of(BookingResourceRoleScheme.VIEWER), resolved.effectiveRole());
    assertTrue(resolved.hasCapability(BookingResourceRoleScheme.READ_RESOURCE));
    assertFalse(resolved.hasCapability(BookingResourceRoleScheme.CREATE_BOOKING));
  }

  @Test
  void doesNotUseAStoredBookingAclWhenTheTargetIsUnreadable() {
    configuration.setResourceAccess(mock(ResourceAccess.class));
    when(inventoryPermissions.canUserReadInventoryRecord(instrument, subject)).thenReturn(false);

    when(instruments.getSafeNull(TARGET_ID)).thenReturn(Optional.of(instrument));
    ResolvedResourceAccess resolved = permissions.resolve(configuration, subject);

    assertEquals(ResolvedResourceAccess.none(), resolved);
  }

  @Test
  void resolvesBulkConfigurationsFromCurrentTargets() {
    BookingConfiguration second = new BookingConfiguration();
    second.setId(24L);
    second.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 18L));
    when(instruments.getBookingRelationshipTargets(eq(java.util.Set.of(TARGET_ID, 18L))))
        .thenReturn(java.util.Map.of(TARGET_ID, instrument));

    assertEquals(
        java.util.Map.of(
            23L,
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.VIEWER),
                new BookingResourceRoleScheme().capabilities(BookingResourceRoleScheme.VIEWER),
                List.of(
                    com.researchspace.model.resourceaccess.ResourceRoleSource.implicit(
                        BookingResourceRoleScheme.VIEWER))),
            24L,
            ResolvedResourceAccess.none()),
        permissions.resolveAll(List.of(configuration, second), subject));
  }
}
