package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Sample;
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
    when(auditManager.getRevisionNumberForDocumentVersion(42L, 3L)).thenReturn(99);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, 3L);

    assertEquals(Long.valueOf(99), rev);
  }

  @Test
  void resolvesRevisionForPinnedGalleryFileVersion() {
    when(auditManager.getRevisionNumberForMediaFileVersion(7L, 2L)).thenReturn(50);

    Long rev = resolver.resolveRevisionForVersion(GlobalIdPrefix.GL, 7L, 2L);

    assertEquals(Long.valueOf(50), rev);
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
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);
    when(user.getUsername()).thenReturn("bob");

    ApiInventoryLinkTargetSummary s =
        resolver.resolveSummary(GlobalIdPrefix.SA, 10L, null, null, user);

    assertEquals("Buffer", s.getName());
  }
}
