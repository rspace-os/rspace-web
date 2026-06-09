package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.AuditDao;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.inventory.Sample;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests for the user-version-to-revision lookup on {@link AuditManagerImpl}, whose null
 * result drives the 404 contract of every inventory version endpoint (RSDEV-1141).
 */
public class AuditManagerVersionTest {

  private final AuditDao auditDao = mock(AuditDao.class);
  private final AuditManagerImpl auditManager = new AuditManagerImpl();

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(auditManager, "auditDao", auditDao);
  }

  @Test
  public void unknownVersionResolvesToNull() {
    when(auditDao.getRevisionsForInventoryRecordVersion(Sample.class, 42L, 99L))
        .thenReturn(Collections.emptyList());

    assertNull(auditManager.getRevisionNumberForInventoryRecordVersion(Sample.class, 42L, 99L));
  }

  @Test
  public void newestRevisionCarryingTheVersionIsReturned() {
    Sample snapshot = mock(Sample.class);
    // the DAO returns revisions newest first; the first one wins
    when(auditDao.getRevisionsForInventoryRecordVersion(Sample.class, 42L, 2L))
        .thenReturn(
            Arrays.asList(
                new AuditedEntity<>(snapshot, 380L), new AuditedEntity<>(snapshot, 200L)));

    assertEquals(
        380L,
        auditManager.getRevisionNumberForInventoryRecordVersion(Sample.class, 42L, 2L).longValue());
  }
}
