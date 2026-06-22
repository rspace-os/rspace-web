package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.inventory.LinkTargetResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for {@link LinkTargetSnapshotResolverImpl}. Collaborators (the audit manager,
 * permission checks) are mocked; the real Envers behaviour is exercised by an author-only IT.
 */
@ExtendWith(MockitoExtension.class)
class LinkTargetSnapshotResolverImplTest {

  @Mock private AuditManager auditManager;
  @Mock private LinkTargetResolver linkTargetResolver;
  @Mock private User user;
  @InjectMocks private LinkTargetSnapshotResolverImpl resolver;

  @Test
  void resolvesRevisionForPinnedStructuredDocumentVersion() {
    when(auditManager.findRevisionNumberForDocumentVersion(42L, 3L)).thenReturn(99);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, 3L);

    assertEquals(Long.valueOf(99), rev);
  }

  @Test
  void resolvesRevisionForPinnedGalleryFileVersion() {
    when(auditManager.findRevisionNumberForMediaFileVersion(7L, 2L)).thenReturn(50);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.GL, 7L, 2L);

    assertEquals(Long.valueOf(50), rev);
  }

  @Test
  void returnsNullForDocumentVersionWithNoAuditRowWithoutCallingTheThrowingLookup() {
    // A version with no audit row (e.g. a freshly created target whose creation revision predates
    // that user-facing version) must degrade to null via the non-throwing lookup. The resolver must
    // NOT call the throwing getRevisionNumberForDocumentVersion, whose IllegalArgumentException -
    // crossing the transactional AuditManager boundary - would mark the caller's transaction
    // rollback-only and fail the whole link save with a 500.
    when(auditManager.findRevisionNumberForDocumentVersion(42L, 1L)).thenReturn(null);

    assertNull(resolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, 1L));
    verify(auditManager, never()).getRevisionNumberForDocumentVersion(anyLong(), any());
  }

  @Test
  void returnsNullForGalleryFileVersionWithNoAuditRowWithoutCallingTheThrowingLookup() {
    when(auditManager.findRevisionNumberForMediaFileVersion(7L, 1L)).thenReturn(null);

    assertNull(resolver.resolveRevisionForVersion(GlobalIdPrefix.GL, 7L, 1L));
    verify(auditManager, never()).getRevisionNumberForMediaFileVersion(anyLong(), any());
  }

  @Test
  void resolvesRevisionForPinnedSampleVersionViaInventoryAuditLookup() {
    when(auditManager.getRevisionNumberForInventoryRecordVersion(Sample.class, 10L, 4L))
        .thenReturn(77);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 10L, 4L);

    assertEquals(Long.valueOf(77), rev);
  }

  @Test
  void resolvesRevisionForSampleTemplateViaSampleClass() {
    when(auditManager.getRevisionNumberForInventoryRecordVersion(Sample.class, 20L, 2L))
        .thenReturn(33);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.IT, 20L, 2L);

    assertEquals(Long.valueOf(33), rev);
  }

  @Test
  void returnsNullForNotebookTargetWhichHasNoUserFacingVersion() {
    assertNull(resolver.resolveRevisionForVersion(GlobalIdPrefix.NB, 5L, 1L));
  }

  @Test
  void returnsNullWhenVersionIsNull() {
    assertNull(resolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, null));
  }

  @Test
  void resolveSummaryDegradesToGlobalIdOnlyWhenNoAuditSnapshotExists() {
    // old databases may hold links whose audit rows were purged; the summary
    // must degrade to the globalId rather than NPE on the missing snapshot
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L)).thenReturn(null);

    ApiInventoryLinkTargetSummary summary =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertEquals("SA10", summary.getGlobalId());
    assertNull(summary.getName());
    assertFalse(summary.isReadable());
  }

  @Test
  void resolveSummaryDegradesToGlobalIdOnlyForUnsupportedPrefix() {
    ApiInventoryLinkTargetSummary summary =
        resolver.resolveSummary(GlobalIdPrefix.FL, 7L, null, null, user);

    assertEquals("FL7", summary.getGlobalId());
    assertNull(summary.getName());
    verify(auditManager, never()).getNewestRevisionForEntity(any(), any());
  }

  @Test
  void resolveSummaryLoadsPinnedRevisionAndBuildsVersionedSummary() {
    Sample rec = mock(Sample.class);
    when(rec.getName()).thenReturn("Buffer");
    when(rec.isDeleted()).thenReturn(false);
    when(auditManager.getObjectForRevision(Sample.class, 10L, 99L))
        .thenReturn(new AuditedEntity<>(rec, 99));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, 3L, 99L, user);

    assertEquals("SA10v3", s.getGlobalId());
    assertEquals("Buffer", s.getName());
    assertEquals("SAMPLE", s.getType());
    assertFalse(s.isDeleted());
    assertTrue(s.isReadable());
    verify(auditManager, never()).getNewestRevisionForEntity(any(), any());
  }

  @Test
  void resolveSummaryUsesNewestRevisionForLatestLink() {
    Sample rec = mock(Sample.class);
    when(rec.getName()).thenReturn("Buffer");
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L))
        .thenReturn(new AuditedEntity<>(rec, 120));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertEquals("SA10", s.getGlobalId());
    assertEquals("Buffer", s.getName());
    verify(auditManager, never()).getObjectForRevision(any(), any(), any());
  }

  @Test
  void resolveSummaryReportsDeletedWhenSnapshotIsSoftDeleted() {
    Sample rec = mock(Sample.class);
    when(rec.getName()).thenReturn("Old buffer");
    when(rec.isDeleted()).thenReturn(true);
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L))
        .thenReturn(new AuditedEntity<>(rec, 120));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertTrue(s.isDeleted());
  }

  @Test
  void resolveSummaryRedactsNameWhenActorCannotReadTarget() {
    Sample rec = mock(Sample.class);
    User owner = mock(User.class);
    when(owner.getUsername()).thenReturn("alice");
    when(rec.getOwner()).thenReturn(owner);
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L))
        .thenReturn(new AuditedEntity<>(rec, 120));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);
    when(user.getUsername()).thenReturn("bob");

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertEquals("SA10", s.getGlobalId());
    assertNull(s.getName());
    assertNull(s.getType());
    assertFalse(s.isReadable());
  }

  @Test
  void resolveSummaryMakesUnreadableTargetIndistinguishableFromNonexistent() {
    // Non-disclosure invariant (ADR-0002): a target the actor cannot read must
    // produce a payload field-for-field identical to one for a record that does
    // not exist, so probing the summary endpoint with guessed ids learns nothing.
    Sample rec = mock(Sample.class);
    User owner = mock(User.class);
    when(owner.getUsername()).thenReturn("alice");
    when(rec.getOwner()).thenReturn(owner);
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L))
        .thenReturn(null)
        .thenReturn(new AuditedEntity<>(rec, 120));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);
    when(user.getUsername()).thenReturn("bob");

    ApiInventoryLinkTargetSummary nonexistent =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);
    ApiInventoryLinkTargetSummary unreadable =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertEquals(nonexistent, unreadable);
  }

  @Test
  void resolveSummaryBuildsSummaryForElnDocumentTarget() {
    StructuredDocument doc = mock(StructuredDocument.class);
    when(doc.getName()).thenReturn("My doc");
    when(doc.isDeleted()).thenReturn(false);
    when(auditManager.getNewestRevisionForEntity(StructuredDocument.class, 42L))
        .thenReturn(new AuditedEntity<>(doc, 5));
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SD, 42L, null, null, user);

    assertEquals("SD42", s.getGlobalId());
    assertEquals("My doc", s.getName());
    assertEquals("DOCUMENT", s.getType());
    assertFalse(s.isDeleted());
    assertTrue(s.isReadable());
  }

  @Test
  void resolveSummaryForDeletedNotebookOwnerReportsDeletedWithoutThrowingLiveCheck() {
    // Notebooks are persisted as Folder rows and Envers audits Folder, not Notebook, so the
    // resolver queries Folder.class (querying Notebook.class threw NotAuditedException). For the
    // owner, readability comes from snapshot ownership and the throwing live folder lookup is
    // skipped: that lookup loads the folder with includeDeleted=false and throws for a deleted
    // folder, which - crossing a transactional boundary - marked the summary transaction
    // rollback-only and 500d it, so the deleted notebook showed no "Target deleted" pill and kept
    // Open. A deleted notebook owned by the viewer must report deleted=true.
    Folder notebook = mock(Folder.class);
    User owner = mock(User.class);
    when(owner.getUsername()).thenReturn("bob");
    when(notebook.getOwner()).thenReturn(owner);
    when(notebook.getName()).thenReturn("Lab notebook");
    when(notebook.isDeleted()).thenReturn(true);
    when(auditManager.getNewestRevisionForEntity(Folder.class, 7L))
        .thenReturn(new AuditedEntity<>(notebook, 12));
    when(user.getUsername()).thenReturn("bob");

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.NB, 7L, null, null, user);

    assertEquals("NB7", s.getGlobalId());
    assertEquals("Lab notebook", s.getName());
    assertEquals("NOTEBOOK", s.getType());
    assertTrue(s.isDeleted());
    assertTrue(s.isReadable());
    verify(linkTargetResolver, never()).targetExistsAndIsReadable(any(), any());
  }

  @Test
  void resolveSummaryPermitsSnapshotOwnerWhenOnlyAuditDataRemains() {
    Sample rec = mock(Sample.class);
    User owner = mock(User.class);
    when(owner.getUsername()).thenReturn("bob");
    when(rec.getOwner()).thenReturn(owner);
    when(rec.getName()).thenReturn("Buffer");
    when(auditManager.getNewestRevisionForEntity(Sample.class, 10L))
        .thenReturn(new AuditedEntity<>(rec, 120));
    when(user.getUsername()).thenReturn("bob");

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    // the snapshot owner is permitted from ownership alone; the live read check is not consulted
    assertEquals("Buffer", s.getName());
    verify(linkTargetResolver, never()).targetExistsAndIsReadable(any(), any());
  }
}
