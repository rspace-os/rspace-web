package com.researchspace.service.inventory.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for the write path of {@link InventoryLinkManagerImpl}: a pinned link captures
 * the resolved Envers revision; a "latest" link leaves it null. The Spring-transactional {@code
 * InventoryLinkManagerImplTest} covers the DB-backed behaviour.
 */
@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplUnitTest {

  @Mock private InventoryLinkDao linkDao;
  @Mock private InventoryPermissionUtils permissionUtils;
  @Mock private LinkTargetResolver linkTargetResolver;
  @Mock private LinkTargetSnapshotResolver snapshotResolver;
  @InjectMocks private InventoryLinkManagerImpl linkManager;

  private final User actor = mock(User.class);

  private void stubReadableTarget() {
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
  }

  private void stubSavedLink() {
    when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createLinkCapturesResolvedRevisionForPinnedTarget() {
    stubReadableTarget();
    stubSavedLink();
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, 3L)).thenReturn(99L);
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SD42v3");

    InventoryLink saved = linkManager.createLink(api, actor);

    assertEquals(Long.valueOf(3), saved.getVersionPin());
    assertEquals(Long.valueOf(99), saved.getTargetRevisionId());
  }

  @Test
  void createLinkLeavesRevisionNullForLatestTarget() {
    stubReadableTarget();
    stubSavedLink();
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SD42");

    InventoryLink saved = linkManager.createLink(api, actor);

    assertNull(saved.getVersionPin());
    assertNull(saved.getTargetRevisionId());
    verify(snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void updateLinkClearsRevisionWhenRepointedToLatest() {
    stubReadableTarget();
    stubSavedLink();
    InventoryLink existing = new InventoryLink();
    existing.setVersionPin(3L);
    existing.setTargetRevisionId(99L);
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("References");
    update.setTargetGlobalId("SD42");

    InventoryLink updated = linkManager.updateLink(existing, update, actor);

    assertNull(updated.getVersionPin());
    assertNull(updated.getTargetRevisionId());
    verify(snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void updateLinkRecapturesRevisionWhenRepointedToPinnedTarget() {
    stubReadableTarget();
    stubSavedLink();
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SD, 42L, 7L)).thenReturn(55L);
    InventoryLink existing = new InventoryLink();
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("References");
    update.setTargetGlobalId("SD42v7");

    InventoryLink updated = linkManager.updateLink(existing, update, actor);

    assertEquals(Long.valueOf(7), updated.getVersionPin());
    assertEquals(Long.valueOf(55), updated.getTargetRevisionId());
  }

  @Test
  void createLinkRejectsAPinToAVersionTheTargetDoesNotHave() {
    stubReadableTarget();
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 6L, 7L)).thenReturn(null);
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SA6v7");

    ApiRuntimeException refused =
        assertThrows(ApiRuntimeException.class, () -> linkManager.createLink(api, actor));

    assertEquals("errors.inventory.field.linkTargetVersionNotFound", refused.getErrorCode());
    assertArrayEquals(new Object[] {"SA6", 7L}, refused.getArgs());
    verify(linkDao, never()).save(any());
  }

  @Test
  void updateLinkRejectsAVersionPinTheTargetDoesNotHave() {
    // the pin arrives as versionPin on an unsuffixed id, which is how the UI sends it
    stubReadableTarget();
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 6L, 7L)).thenReturn(null);
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("References");
    update.setTargetGlobalId("SA6");
    update.setVersionPin(7L);

    ApiRuntimeException refused =
        assertThrows(
            ApiRuntimeException.class,
            () -> linkManager.updateLink(new InventoryLink(), update, actor));

    assertEquals("errors.inventory.field.linkTargetVersionNotFound", refused.getErrorCode());
    verify(linkDao, never()).save(any());
  }

  @Test
  void anUnreadableTargetFailsAsNotFoundBeforeItsVersionsAreLookedAt() {
    // ADR 0002: a missing item and an unreadable one must keep looking the same, so the
    // version is never consulted for a target the actor cannot read
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SA6v7");

    ApiRuntimeException refused =
        assertThrows(ApiRuntimeException.class, () -> linkManager.createLink(api, actor));

    assertEquals("errors.inventory.field.linkTargetNotFound", refused.getErrorCode());
    verify(snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void findReferencingItemsRejectsTargetTheCallerCannotReadWithoutQuerying() {
    // unreadable and missing targets deliberately produce the same error, so the
    // response does not confirm to a guessing caller that the record exists
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);

    assertThrows(ApiRuntimeException.class, () -> linkManager.findReferencingItems("SA42", actor));
    verify(linkDao, never()).findReferencingLinkFields(any(), any());
  }

  @Test
  void findReferencingItemsQueriesSourcesWhenTargetIsReadable() {
    stubReadableTarget();
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SA, 42L))
        .thenReturn(java.util.Collections.emptyList());

    assertThat(linkManager.findReferencingItems("SA42", actor)).isEmpty();
  }

  @Test
  void findReferencingItemsRejectsMalformedGlobalId() {
    assertThrows(
        ApiRuntimeException.class, () -> linkManager.findReferencingItems("not-a-gid", actor));
    verify(linkDao, never()).findReferencingLinkFields(any(), any());
  }

  @Test
  void targetSummaryForGlobalIdResolvesCurrentStateOfTheBaseRecord() {
    // the summary backs the "Target deleted" pill, which must reflect the
    // record as it is NOW: the pin/revision are deliberately not consulted,
    // and a version suffix on the id does not change that state
    ApiInventoryLinkTargetSummary expected = new ApiInventoryLinkTargetSummary();
    expected.setGlobalId("SD42");
    expected.setDeleted(true);
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SD, 42L, null, null, actor))
        .thenReturn(expected);

    assertSame(expected, linkManager.getTargetSummary("SD42v3", actor));
  }

  @Test
  void targetSummaryForAVersionedIdSaysWhetherThatVersionResolvesForAReader() {
    ApiInventoryLinkTargetSummary readable = new ApiInventoryLinkTargetSummary();
    readable.setGlobalId("SA6");
    readable.setReadable(true);
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SA, 6L, null, null, actor))
        .thenReturn(readable);
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 6L, 7L)).thenReturn(null);

    assertEquals(
        Boolean.FALSE, linkManager.getTargetSummary("SA6v7", actor).getPinnedVersionFound());
  }

  @Test
  void targetSummaryForAVersionedIdReportsAResolvedVersionAsFound() {
    ApiInventoryLinkTargetSummary readable = new ApiInventoryLinkTargetSummary();
    readable.setGlobalId("SA6");
    readable.setReadable(true);
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SA, 6L, null, null, actor))
        .thenReturn(readable);
    when(snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, 6L, 1L)).thenReturn(44L);

    assertEquals(
        Boolean.TRUE, linkManager.getTargetSummary("SA6v1", actor).getPinnedVersionFound());
  }

  @Test
  void targetSummaryForAVersionedIdOfAnUnreadableTargetSaysNothingAboutVersions() {
    // ADR 0002: the redacted payload stays field-for-field what it is for a missing item
    ApiInventoryLinkTargetSummary redacted = new ApiInventoryLinkTargetSummary();
    redacted.setGlobalId("SA6");
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SA, 6L, null, null, actor))
        .thenReturn(redacted);

    assertNull(linkManager.getTargetSummary("SA6v7", actor).getPinnedVersionFound());
    verify(snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void targetSummaryForAnUnpinnedIdAsksNothingAboutVersions() {
    ApiInventoryLinkTargetSummary readable = new ApiInventoryLinkTargetSummary();
    readable.setGlobalId("SA6");
    readable.setReadable(true);
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SA, 6L, null, null, actor))
        .thenReturn(readable);

    assertNull(linkManager.getTargetSummary("SA6", actor).getPinnedVersionFound());
    verify(snapshotResolver, never()).resolveRevisionForVersion(any(), any(), any());
  }

  @Test
  void targetSummaryRejectsMalformedAndUnsupportedIdsWithWritePathErrors() {
    ApiRuntimeException malformed =
        assertThrows(
            ApiRuntimeException.class, () -> linkManager.getTargetSummary("not-a-gid", actor));
    assertEquals("errors.inventory.field.linkTargetNotFound", malformed.getErrorCode());

    // FL is not an allowed link target kind, so its summary must not resolve
    ApiRuntimeException unsupported =
        assertThrows(ApiRuntimeException.class, () -> linkManager.getTargetSummary("FL3", actor));
    assertEquals("errors.inventory.field.linkTargetKindUnsupported", unsupported.getErrorCode());
  }
}
