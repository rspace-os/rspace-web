package com.researchspace.service.impl;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class FeatureFlagManagerImplIT extends RealTransactionSpringTestBase {

  @Autowired private FeatureFlagManager featureFlags;

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
                  BOOKING_ENABLED, new Patch(true, false, null), sysadmin);
              secondResponse.set(
                  featureFlags
                      .updateFeatureFlag(BOOKING_ENABLED, new Patch(true, false, null), sysadmin)
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
                  BOOKING_ENABLED, new Patch(true, false, null), sysadmin);
              assertFalse(
                  featureFlags
                      .updateFeatureFlag(BOOKING_ENABLED, new Patch(false, false, null), sysadmin)
                      .orElseThrow()
                      .isBaselineValue());
            });
    assertFalse(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, (User) null));
  }

  private void setBaseline(boolean value) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                featureFlags.updateFeatureFlag(
                    BOOKING_ENABLED, new Patch(value, false, null), sysadmin));
  }
}
