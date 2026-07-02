package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class LinkTargetResolverImplTest {

  @Mock private InventoryPermissionUtils inventoryPermissionUtils;
  @Mock private BaseRecordManager baseRecordManager;
  @Mock private IPermissionUtils permissionUtils;
  @InjectMocks private LinkTargetResolverImpl resolver;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("any");
  }

  @Test
  void resolutionAppliesPendingPermissionCacheRefreshBeforeChecking() {
    // an unshare notifies the affected user to refresh their cached Shiro
    // authorisation; resolution must apply that pending refresh first, or the
    // viewer keeps the stale read grant (and a working Open with no pill)
    // until the server restarts
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenReturn(Collections.emptyList());

    resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD42"), user);

    InOrder inOrder = inOrder(permissionUtils, baseRecordManager);
    inOrder.verify(permissionUtils).refreshCacheIfNotified();
    inOrder.verify(baseRecordManager).getByGlobalIdsAndReadPermission(any(), eq(user));
  }

  @Test
  void inventoryResolutionAlsoAppliesPendingPermissionCacheRefreshFirst() {
    when(inventoryPermissionUtils.canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(true);

    resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user);

    InOrder inOrder = inOrder(permissionUtils, inventoryPermissionUtils);
    inOrder.verify(permissionUtils).refreshCacheIfNotified();
    inOrder
        .verify(inventoryPermissionUtils)
        .canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user));
  }

  @Test
  void inventoryTargetReadableResolvesTrue() {
    when(inventoryPermissionUtils.canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(true);

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
  }

  @Test
  void inventoryTargetNotReadableResolvesFalse() {
    when(inventoryPermissionUtils.canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(false);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
  }

  @Test
  void inventoryLinkingRequiresFullReadNotLimitedRead() {
    // the inventory API grants any logged-in user a redacted "limited read"
    // view of items, but that must never be enough to link to them: the
    // resolver consults the full READ check only, so an unreadable target is
    // rejected exactly like a missing one and existence is not disclosed
    when(inventoryPermissionUtils.canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenReturn(false);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
    verify(inventoryPermissionUtils, never())
        .canUserLimitedReadInventoryRecord(any(GlobalIdentifier.class), eq(user));
  }

  @Test
  void inventoryTargetNotFoundResolvesFalse() {
    when(inventoryPermissionUtils.canUserReadInventoryRecord(any(GlobalIdentifier.class), eq(user)))
        .thenThrow(new NotFoundException("no such record"));

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA9999"), user));
  }

  @Test
  void elnDocumentTargetReadableResolvesTrue() {
    BaseRecord document = org.mockito.Mockito.mock(BaseRecord.class);
    when(document.getOid()).thenReturn(new GlobalIdentifier("SD123"));
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenReturn(List.of(document));

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void elnTargetNotReadableResolvesFalse() {
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenReturn(Collections.emptyList());

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void elnTargetNotFoundResolvesFalse() {
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenThrow(new ObjectRetrievalFailureException("BaseRecord", 123L));

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void notebookAndGalleryTargetsResolveViaBaseRecordManager() {
    BaseRecord notebook = org.mockito.Mockito.mock(BaseRecord.class);
    when(notebook.getOid()).thenReturn(new GlobalIdentifier("NB7"));
    BaseRecord galleryFile = org.mockito.Mockito.mock(BaseRecord.class);
    when(galleryFile.getOid()).thenReturn(new GlobalIdentifier("GL55"));
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenReturn(List.of(notebook), List.of(galleryFile));

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NB7"), user));
    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("GL55"), user));
  }

  @Test
  void versionSuffixIsStrippedBeforeResolving() {
    ArgumentCaptor<List<GlobalIdentifier>> captor = ArgumentCaptor.forClass(List.class);
    when(baseRecordManager.getByGlobalIdsAndReadPermission(captor.capture(), eq(user)))
        .thenReturn(List.of(org.mockito.Mockito.mock(BaseRecord.class)));

    resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123v5"), user);

    GlobalIdentifier resolved = captor.getValue().get(0);
    assertFalse(resolved.hasVersionId(), "version suffix should be stripped before resolving");
    assertEquals(Long.valueOf(123), resolved.getDbId());
    assertEquals(GlobalIdPrefix.SD, resolved.getPrefix());
  }

  @Test
  void elnTargetMustMatchRequestedPrefixNotJustDbId() {
    // the workspace loader resolves by numeric id alone, so "GL150" loads
    // whatever record has id 150 (e.g. folder FL150); only a record whose own
    // oid prefix matches the requested one may count as the link target
    BaseRecord folder = org.mockito.Mockito.mock(BaseRecord.class);
    when(folder.getOid()).thenReturn(new GlobalIdentifier("FL150"));
    when(baseRecordManager.getByGlobalIdsAndReadPermission(any(), eq(user)))
        .thenReturn(List.of(folder));

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("GL150"), user));
  }

  @Test
  void unsupportedPrefixResolvesFalse() {
    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("FM3"), user));
  }

  @Test
  void folderTargetResolvesFalseWithoutQuerying() {
    // FL is not an allowed link target kind (InventoryLinkValidator rejects it),
    // so the resolver must not treat readable folders as resolvable: doing so
    // would let the referencing-items endpoint return an empty list instead of
    // the uniform not-found error, disclosing folder readability via a
    // side-channel
    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("FL3"), user));
    verify(baseRecordManager, never()).getByGlobalIdsAndReadPermission(any(), eq(user));
  }

  @Test
  void nullTargetResolvesFalse() {
    assertFalse(resolver.targetExistsAndIsReadable(null, user));
  }
}
