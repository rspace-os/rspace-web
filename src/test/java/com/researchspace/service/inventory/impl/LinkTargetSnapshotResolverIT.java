package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
  private @Autowired InventoryLinkManager linkManager;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void resolvesUserVersionToRevisionForNewlyCreatedSample() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    Long revision =
        snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, sample.getId(), 1L);

    assertNotNull(revision, "version 1 of a sample should map to an Envers revision");
  }

  @Test
  public void resolvesLatestSnapshotFromNewestRevision() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    // resolveSummary is non-transactional by design: in production it runs inside the calling
    // *Manager's transaction. Its Envers reads and lazy entity hydration need a thread-bound
    // session, so the IT supplies one rather than calling the resolver bare.
    ApiInventoryLinkTargetSummary summary =
        doInTransaction(
            () -> {
              return snapshotResolver.resolveSummary(
                  GlobalIdPrefix.SA, sample.getId(), null, null, user);
            });

    assertEquals("SA" + sample.getId(), summary.getGlobalId());
    assertEquals(sample.getName(), summary.getName());
    assertEquals("SAMPLE", summary.getType());
    assertFalse(summary.isDeleted());
  }

  @Test
  public void resolvesPinnedSnapshotAtStoredRevision() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long revision =
        snapshotResolver.resolveRevisionForVersion(GlobalIdPrefix.SA, sample.getId(), 1L);

    // see resolvesLatestSnapshotFromNewestRevision: resolveSummary needs an active transaction
    ApiInventoryLinkTargetSummary summary =
        doInTransaction(
            () -> {
              return snapshotResolver.resolveSummary(
                  GlobalIdPrefix.SA, sample.getId(), 1L, revision, user);
            });

    assertEquals("SA" + sample.getId() + "v1", summary.getGlobalId());
    assertEquals(sample.getName(), summary.getName());
    assertEquals("SAMPLE", summary.getType());
  }

  /**
   * RSDEV-1354: audit rows can be purged while the record they describe lives on. A missing
   * snapshot used to be reported as the redacted "No access" summary unconditionally, which took
   * Open away from a target whose page still works. The resolver now falls back to asking whether
   * this actor can read the live record.
   */
  @Test
  public void reportsLiveReadableTargetWhoseAuditRowsWerePurgedAsReadable() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    new JdbcTemplate(dataSource).update("DELETE FROM Sample_AUD WHERE id = ?", sample.getId());

    // see resolvesLatestSnapshotFromNewestRevision: resolveSummary needs an active transaction
    ApiInventoryLinkTargetSummary summary =
        doInTransaction(
            () -> {
              return snapshotResolver.resolveSummary(
                  GlobalIdPrefix.SA, sample.getId(), null, null, user);
            });

    assertTrue(
        summary.isReadable(),
        "a live record the actor can read stays readable when its audit rows are gone");
  }

  /**
   * RSDEV-1354: the purged-audit fallback must not resurrect a dead target. {@code
   * targetExistsAndIsReadable} is deliberately true for a readable soft-deleted record, so the
   * fallback asks the live check instead; otherwise a deleted sample whose audit rows were purged
   * would lose its "No access" pill and keep an Open action.
   */
  @Test
  public void reportsSoftDeletedTargetWhoseAuditRowsWerePurgedAsUnreadable() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    sampleApiMgr.markSampleAsDeleted(sample.getId(), false, user);
    new JdbcTemplate(dataSource).update("DELETE FROM Sample_AUD WHERE id = ?", sample.getId());

    // see resolvesLatestSnapshotFromNewestRevision: resolveSummary needs an active transaction
    ApiInventoryLinkTargetSummary summary =
        doInTransaction(
            () -> {
              return snapshotResolver.resolveSummary(
                  GlobalIdPrefix.SA, sample.getId(), null, null, user);
            });

    assertFalse(
        summary.isReadable(),
        "a soft-deleted record must stay redacted when its audit rows are gone");
  }

  /**
   * RSDEV-1354: samples and sample templates are one table split by DTYPE, so they share a numeric
   * id space, and the retriever resolves both SA and IT through the same lookup. Envers finds no
   * SampleTemplate revision for a sample's id, so a forged "IT&lt;sampleId&gt;" falls into the
   * purged-audit fallback; without a type-exact check the readable sample would vouch for a
   * template that does not exist, disclosing that id is readable (ADR-0002).
   */
  @Test
  public void reportsTemplateRequestCollidingWithReadableSampleIdAsUnreadable() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    // see resolvesLatestSnapshotFromNewestRevision: resolveSummary needs an active transaction
    ApiInventoryLinkTargetSummary summary =
        doInTransaction(
            () -> {
              return snapshotResolver.resolveSummary(
                  GlobalIdPrefix.IT, sample.getId(), null, null, user);
            });

    assertEquals("IT" + sample.getId(), summary.getGlobalId());
    assertFalse(
        summary.isReadable(), "a readable sample must not vouch for a template sharing its id");
  }

  /**
   * RSDEV-1354: the purged-audit fallback must never run the ELN lookup. That lookup goes through
   * transactional {@code *Manager} proxies and throws for a missing, unreadable or deleted record;
   * the throw marks the caller's transaction rollback-only even though it is caught, so {@code
   * getTargetSummary} would fail at commit with {@code UnexpectedRollbackException} rather than
   * return the redacted summary. Import can store a link to a notebook that does not exist, so this
   * is reachable from the UI. Exercised through the transactional manager, not the resolver, since
   * that is where the rollback surfaces.
   */
  @Test
  public void reportsDanglingNotebookTargetAsRedactedWithoutPoisoningTheTransaction() {
    User user = createInitAndLoginAnyUser();

    ApiInventoryLinkTargetSummary summary = linkManager.getTargetSummary("NB99999999", user);

    assertEquals("NB99999999", summary.getGlobalId());
    assertFalse(summary.isReadable(), "a target that cannot be resolved stays redacted");
  }
}
