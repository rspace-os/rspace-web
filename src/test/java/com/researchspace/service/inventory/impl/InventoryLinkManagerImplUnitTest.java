package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  void setUp() {
    lenient().when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
    lenient().when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void createLinkCapturesResolvedRevisionForPinnedTarget() {
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
  void getTargetSummaryThreadsStoredRevisionToResolver() {
    InventoryLink link = new InventoryLink();
    link.setTargetPrefix(GlobalIdPrefix.SA);
    link.setTargetDbId(10L);
    link.setVersionPin(3L);
    link.setTargetRevisionId(99L);
    ApiInventoryLinkTargetSummary expected = new ApiInventoryLinkTargetSummary();
    when(snapshotResolver.resolveSummary(GlobalIdPrefix.SA, 10L, 3L, 99L, actor))
        .thenReturn(expected);

    ApiInventoryLinkTargetSummary result = linkManager.getTargetSummary(link, actor);

    assertSame(expected, result);
  }
}
