package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

  /** Stubs the retriever to return a readable record whose own oid matches the requested id. */
  private InventoryRecord givenReadableInventoryRecord(String globalId) {
    InventoryRecord record = mock(InventoryRecord.class);
    when(record.getOid()).thenReturn(new GlobalIdentifier(globalId));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(record);
    when(inventoryPermissionUtils.canUserReadInventoryRecord(eq(record), eq(user)))
        .thenReturn(true);
    return record;
  }

  @Test
  void resolutionAppliesPendingPermissionCacheRefreshBeforeChecking() {
    // an unshare notifies the affected user to refresh their cached Shiro
    // authorisation; resolution must apply that pending refresh first, or the
    // viewer keeps the stale read grant (and a working Open with no pill)
    // until the server restarts
    givenElnRecord("SD42", true);

    resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD42"), user);

    InOrder inOrder = inOrder(permissionUtils);
    inOrder.verify(permissionUtils).refreshCacheIfNotified();
    inOrder.verify(permissionUtils).filter(anyList(), eq(PermissionType.READ), eq(user));
  }

  @Test
  void inventoryResolutionAlsoAppliesPendingPermissionCacheRefreshFirst() {
    givenReadableInventoryRecord("SA42");

    resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user);

    InOrder inOrder = inOrder(permissionUtils, inventoryPermissionUtils);
    inOrder.verify(permissionUtils).refreshCacheIfNotified();
    inOrder
        .verify(inventoryPermissionUtils)
        .getInvRecByGlobalIdOrThrowNotFoundException(any(GlobalIdentifier.class));
  }

  @Test
  void inventoryTargetReadableResolvesTrue() {
    givenReadableInventoryRecord("SA42");

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
  }

  @Test
  void instrumentTargetResolvesThroughInventoryReadabilityCheck() {
    givenReadableInventoryRecord("IN42");

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("IN42"), user));

    ArgumentCaptor<GlobalIdentifier> gid = ArgumentCaptor.forClass(GlobalIdentifier.class);
    verify(inventoryPermissionUtils).getInvRecByGlobalIdOrThrowNotFoundException(gid.capture());
    assertEquals(GlobalIdPrefix.IN, gid.getValue().getPrefix());
  }

  @Test
  void instrumentTemplateTargetResolvesThroughInventoryReadabilityCheck() {
    givenReadableInventoryRecord("NT42");

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NT42"), user));
  }

  @Test
  void inventoryTargetNotReadableResolvesFalse() {
    InventoryRecord record = mock(InventoryRecord.class);
    when(record.getOid()).thenReturn(new GlobalIdentifier("SA42"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(record);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
  }

  @Test
  void inventoryLinkingRequiresFullReadNotLimitedRead() {
    // the inventory API grants any logged-in user a redacted "limited read"
    // view of items, but that must never be enough to link to them: the
    // resolver consults the full READ check only, so an unreadable target is
    // rejected exactly like a missing one and existence is not disclosed
    InventoryRecord record = mock(InventoryRecord.class);
    when(record.getOid()).thenReturn(new GlobalIdentifier("SA42"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(record);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA42"), user));
    verify(inventoryPermissionUtils, never())
        .canUserLimitedReadInventoryRecord(any(GlobalIdentifier.class), eq(user));
  }

  @Test
  void inventoryTargetNotFoundResolvesFalse() {
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenThrow(new NotFoundException("no such record"));

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA9999"), user));
  }

  /** Stubs the ELN loader to return a record with this Global ID, readable by the user or not. */
  private BaseRecord givenElnRecord(String globalId, boolean readable) {
    GlobalIdentifier gid = new GlobalIdentifier(globalId);
    BaseRecord record = mock(BaseRecord.class);
    when(record.getOid()).thenReturn(gid);
    when(baseRecordManager.getSafeNull(gid.getDbId())).thenReturn(Optional.of(record));
    // lenient: the prefix and trash checks come first and can short-circuit past it
    lenient()
        .when(
            permissionUtils.filter(
                argThat((List<BaseRecord> records) -> records != null && records.contains(record)),
                eq(PermissionType.READ),
                eq(user)))
        .thenAnswer(invocation -> readable ? invocation.getArgument(0) : new ArrayList<>());
    return record;
  }

  /**
   * isPermitted lets any user READ a published record; linking to one never counted that, so the
   * READ check is the filter without that shortcut, as the lookup this replaced applied it.
   */
  @Test
  void aPublishedRecordNotSharedWithTheUserIsNotALinkTarget() {
    givenElnRecord("SD123", false);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
    verify(permissionUtils, never()).isPermitted(any(), any(), any());
  }

  @Test
  void elnDocumentTargetReadableResolvesTrue() {
    givenElnRecord("SD123", true);

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void elnTargetNotReadableResolvesFalse() {
    givenElnRecord("SD123", false);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void elnTargetNotFoundResolvesFalse() {
    when(baseRecordManager.getSafeNull(123L)).thenReturn(Optional.empty());

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void notebookAndGalleryTargetsResolveViaBaseRecordManager() {
    when(givenElnRecord("NB7", true).isFolder()).thenReturn(true);
    givenElnRecord("GL55", true);

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NB7"), user));
    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("GL55"), user));
  }

  @Test
  void aTrashedNotebookIsNotALinkTarget() {
    BaseRecord notebook = givenElnRecord("NB7", true);
    when(notebook.isFolder()).thenReturn(true);
    when(notebook.isDeleted()).thenReturn(true);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NB7"), user));
  }

  /** The other two ways FolderManager.getFolder counts a folder as trashed. */
  @Test
  void aNotebookTrashedForItsOwnerOrForTheUserIsNotALinkTarget() {
    User owner = new User("owner");
    BaseRecord trashedByOwner = givenElnRecord("NB7", true);
    when(trashedByOwner.isFolder()).thenReturn(true);
    when(trashedByOwner.getOwner()).thenReturn(owner);
    when(trashedByOwner.isDeletedForUser(owner)).thenReturn(true);
    BaseRecord trashedByUser = givenElnRecord("NB8", true);
    when(trashedByUser.isFolder()).thenReturn(true);
    when(trashedByUser.getOwner()).thenReturn(owner);
    when(trashedByUser.isDeletedForUser(owner)).thenReturn(false);
    when(trashedByUser.isDeletedForUser(user)).thenReturn(true);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NB7"), user));
    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("NB8"), user));
  }

  /** Only a folder or notebook in the trash stops being a target; a trashed document does not. */
  @Test
  void aTrashedDocumentTheUserCanReadIsStillALinkTargetButNotALiveOne() {
    BaseRecord document = givenElnRecord("SD123", true);
    when(document.isDeleted()).thenReturn(true);

    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SD123"), user));
    assertFalse(resolver.targetIsLiveAndReadable(new GlobalIdentifier("SD123"), user));
  }

  @Test
  void elnTargetMustMatchRequestedPrefixNotJustDbId() {
    // the workspace loader resolves by numeric id alone, so "GL150" loads
    // whatever record has id 150 (e.g. folder FL150); only a record whose own
    // oid prefix matches the requested one may count as the link target
    givenElnRecord("FL150", true);

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
    verify(baseRecordManager, never()).getSafeNull(any());
  }

  @Test
  void liveInventoryTargetMatchingRequestedPrefixResolvesTrue() {
    InventoryRecord template = mock(InventoryRecord.class);
    when(template.getOid()).thenReturn(new GlobalIdentifier("IT90"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(template);
    when(inventoryPermissionUtils.canUserReadInventoryRecord(eq(template), eq(user)))
        .thenReturn(true);

    assertTrue(resolver.targetIsLiveAndReadable(new GlobalIdentifier("IT90"), user));
  }

  @Test
  void inventoryTargetMustMatchRequestedPrefixNotJustDbId() {
    // samples and sample templates share one numeric id space and the retriever resolves both
    // SA and IT through the same lookup, so "IT90" can load sample SA90. Only a record whose own
    // oid prefix matches the requested one is the link target; otherwise a readable sibling
    // would vouch for a template that does not exist
    InventoryRecord sample = mock(InventoryRecord.class);
    when(sample.getOid()).thenReturn(new GlobalIdentifier("SA90"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(sample);

    assertFalse(resolver.targetIsLiveAndReadable(new GlobalIdentifier("IT90"), user));
  }

  @Test
  void softDeletedInventoryTargetIsReadableButNotLive() {
    // the two differ deliberately: a registry entry must not name a dead record, but the link
    // card still shows a trashed item, whose trash viewer works
    InventoryRecord sample = mock(InventoryRecord.class);
    when(sample.getOid()).thenReturn(new GlobalIdentifier("SA90"));
    when(sample.isDeleted()).thenReturn(true);
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(sample);
    when(inventoryPermissionUtils.canUserReadInventoryRecord(eq(sample), eq(user)))
        .thenReturn(true);

    assertFalse(resolver.targetIsLiveAndReadable(new GlobalIdentifier("SA90"), user));
    assertTrue(resolver.viewableInventoryTarget(new GlobalIdentifier("SA90"), user).isPresent());
  }

  @Test
  void existsAndReadableMustMatchRequestedPrefixNotJustDbId() {
    // this method gates link CREATION and findReferencingItems, so resolving by number alone let
    // a readable sample SA90 authorise a link to an IT90 that does not exist
    InventoryRecord sample = mock(InventoryRecord.class);
    when(sample.getOid()).thenReturn(new GlobalIdentifier("SA90"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(sample);

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("IT90"), user));
  }

  @Test
  void existsAndReadableStaysTrueForAReadableSoftDeletedRecord() {
    // deliberately deleted-tolerant: only targetIsLiveAndReadable adds the not-deleted filter,
    // so a trashed target the actor can read is still a legitimate link target here
    InventoryRecord sample = mock(InventoryRecord.class);
    when(sample.getOid()).thenReturn(new GlobalIdentifier("SA90"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(sample);
    when(inventoryPermissionUtils.canUserReadInventoryRecord(eq(sample), eq(user)))
        .thenReturn(true);

    // the deleted flag is never consulted on this path, which is the contract being pinned
    assertTrue(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA90"), user));
    verify(sample, never()).isDeleted();
  }

  @Test
  void viewableInventoryTargetIsEmptyForWrongPrefixSibling() {
    InventoryRecord sample = mock(InventoryRecord.class);
    when(sample.getOid()).thenReturn(new GlobalIdentifier("SA90"));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(sample);

    assertFalse(resolver.viewableInventoryTarget(new GlobalIdentifier("IT90"), user).isPresent());
  }

  @Test
  void viewableInventoryTargetIsEmptyForElnPrefix() {
    assertFalse(resolver.viewableInventoryTarget(new GlobalIdentifier("NB7"), user).isPresent());
    verify(baseRecordManager, never()).getSafeNull(any());
  }

  @Test
  void nullTargetResolvesFalse() {
    assertFalse(resolver.targetExistsAndIsReadable(null, user));
  }

  @Test
  void viewableInventoryTargetCountsLimitedRead() {
    // an item reached through a container, a list of materials or a template opens in the
    // limited view, so the link card must not call it "No access"
    limitedReadOnly("SA90");

    assertTrue(resolver.viewableInventoryTarget(new GlobalIdentifier("SA90"), user).isPresent());
  }

  @Test
  void limitedReadIsNotEnoughToLinkToATarget() {
    limitedReadOnly("SA90");

    assertFalse(resolver.targetExistsAndIsReadable(new GlobalIdentifier("SA90"), user));
  }

  private InventoryRecord limitedReadOnly(String globalId) {
    InventoryRecord rec = mock(InventoryRecord.class);
    when(rec.getOid()).thenReturn(new GlobalIdentifier(globalId));
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(
            any(GlobalIdentifier.class)))
        .thenReturn(rec);
    when(inventoryPermissionUtils.canUserReadInventoryRecord(eq(rec), eq(user))).thenReturn(false);
    lenient()
        .when(inventoryPermissionUtils.canUserLimitedReadInventoryRecord(eq(rec), eq(user)))
        .thenReturn(true);
    return rec;
  }
}
