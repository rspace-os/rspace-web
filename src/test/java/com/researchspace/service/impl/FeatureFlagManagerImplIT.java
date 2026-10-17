package com.researchspace.service.impl;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class FeatureFlagManagerImplIT extends RealTransactionSpringTestBase {

  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private FeatureFlagDao featureFlagDao;

  private User sysadmin;
  private boolean originalBaseline;

  @BeforeEach
  void setUpFeatureFlag() throws Exception {
    super.setUp();
    sysadmin = getSysAdminUser();
    originalBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBaseline(false);
  }

  @AfterEach
  void restoreFeatureFlag() throws Exception {
    setBaseline(originalBaseline);
    super.tearDown();
  }

  @Test
  void repeatedBaselinePatchReadsTheValueWrittenEarlierInTheSameTransaction() {
    AtomicReference<Boolean> secondResponse = new AtomicReference<>();
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              featureFlags.updateFeatureFlag(
                  BOOKING_ENABLED, new Patch(true, false, null), sysadmin, sysadmin);
              secondResponse.set(
                  featureFlags
                      .updateFeatureFlag(
                          BOOKING_ENABLED, new Patch(true, false, null), sysadmin, sysadmin)
                      .orElseThrow()
                      .isBaselineValue());
            });

    assertTrue(secondResponse.get());
    assertTrue(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, (User) null));
  }

  @Test
  void finalBaselineChangeWinsWithinOneTransaction() {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              featureFlags.updateFeatureFlag(
                  BOOKING_ENABLED, new Patch(true, false, null), sysadmin, sysadmin);
              assertFalse(
                  featureFlags
                      .updateFeatureFlag(
                          BOOKING_ENABLED, new Patch(false, false, null), sysadmin, sysadmin)
                      .orElseThrow()
                      .isBaselineValue());
            });
    assertFalse(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, (User) null));
  }

  @Test
  void laterCommittedBaselineWinsWhenEarlierAfterCommitCallbackRunsLast() throws Exception {
    CountDownLatch earlierCommitReachedAfterCommit = new CountDownLatch(1);
    CountDownLatch releaseEarlierCallback = new CountDownLatch(1);
    Subject testSubject = SecurityUtils.getSubject();
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      Future<?> earlierTransaction =
          executor.submit(
              () -> {
                ThreadContext.bind(testSubject);
                try {
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            TransactionSynchronizationManager.registerSynchronization(
                                new TransactionSynchronization() {
                                  @Override
                                  public int getOrder() {
                                    return Ordered.HIGHEST_PRECEDENCE;
                                  }

                                  @Override
                                  public void afterCommit() {
                                    earlierCommitReachedAfterCommit.countDown();
                                    try {
                                      if (!releaseEarlierCallback.await(30, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                            "Timed out waiting to release the earlier baseline"
                                                + " callback");
                                      }
                                    } catch (InterruptedException e) {
                                      Thread.currentThread().interrupt();
                                      throw new IllegalStateException(
                                          "Interrupted while awaiting release of the earlier"
                                              + " baseline callback",
                                          e);
                                    }
                                  }
                                });
                            User transactionSysadmin = getSysAdminUser();
                            featureFlags.updateFeatureFlag(
                                BOOKING_ENABLED,
                                new Patch(true, false, null),
                                transactionSysadmin,
                                transactionSysadmin);
                          });
                } finally {
                  ThreadContext.unbindSubject();
                }
              });

      try {
        assertTrue(
            earlierCommitReachedAfterCommit.await(30, TimeUnit.SECONDS),
            "earlier baseline transaction did not reach afterCommit");
        setBaseline(false);
        assertFalse(
            featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue());
      } finally {
        releaseEarlierCallback.countDown();
      }

      earlierTransaction.get(30, TimeUnit.SECONDS);
      Boolean persistedBaseline =
          new TransactionTemplate(getTxMger())
              .execute(ignored -> featureFlagDao.getBaselineValues().get(BOOKING_ENABLED));
      assertFalse(persistedBaseline);
      assertFalse(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, (User) null));
    } finally {
      releaseEarlierCallback.countDown();
      executor.shutdownNow();
      assertTrue(
          executor.awaitTermination(30, TimeUnit.SECONDS),
          "earlier baseline transaction did not terminate");
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(ignored -> featureFlags.reconcileOnStartup());
    }
  }

  private void setBaseline(boolean value) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                featureFlags.updateFeatureFlag(
                    BOOKING_ENABLED, new Patch(value, false, null), sysadmin, sysadmin));
  }
}
