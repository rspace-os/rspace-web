package com.researchspace.service.inventory.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end Envers test for {@link LinkTargetSnapshotResolverImpl}, exercising real audit data:
 * the user-facing version -> Envers REV mapping, the newest-revision "latest" resolution, and
 * loading a pinned revision into a summary.
 *
 * <p>Author-only: NOT run during implementation (see project test policy). Envers only writes audit
 * rows on a real commit, so this extends {@link RealTransactionSpringTestBase} (real commits, *IT
 * suffix) rather than the auto-rolled-back transactional base.
 */
public class LinkTargetSnapshotResolverIT extends RealTransactionSpringTestBase {

  private @Autowired LinkTargetSnapshotResolver snapshotResolver;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void resolvesUserVersionToRevisionForNewlyCreatedSample() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    Long revision =
        snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, sample.getId(), 1L);

    assertNotNull("version 1 of a sample should map to an Envers revision", revision);
  }

  @Test
  public void resolvesLatestSnapshotFromNewestRevision() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    ApiInventoryLinkTargetSummary summary =
        snapshotResolver.resolveSummary(GlobalIdPrefix.SA, sample.getId(), null, null, user);

    assertEquals("SA" + sample.getId(), summary.getGlobalId());
    assertEquals(sample.getName(), summary.getName());
    assertEquals("SAMPLE", summary.getType());
    assertFalse(summary.isDeleted());
  }

  @Test
  public void resolvesPinnedSnapshotAtStoredRevision() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long revision =
        snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, sample.getId(), 1L);

    ApiInventoryLinkTargetSummary summary =
        snapshotResolver.resolveSummary(GlobalIdPrefix.SA, sample.getId(), 1L, revision, user);

    assertEquals("SA" + sample.getId() + "v1", summary.getGlobalId());
    assertEquals(sample.getName(), summary.getName());
    assertEquals("SAMPLE", summary.getType());
  }
}
