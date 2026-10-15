package com.researchspace.service.impl;

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

  // Declared only in src/test/resources/feature-flags/feature-flags.jsonc, so these tests do not
  // depend on a real flag that will later be removed.
  private static final String FLAG = "testFlag";

  @Autowired private FeatureFlagManager featureFlags;

  private User sysadmin;
  private boolean originalBaseline;

  @BeforeEach
  void setUpFeatureFlag() throws Exception {
    super.setUp();
    sysadmin = getSysAdminUser();
    originalBaseline = featureFlags.getFeatureFlag(FLAG, sysadmin).orElseThrow().isBaselineValue();
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
                  FLAG, new Patch(true, false, null), sysadmin, sysadmin);
              secondResponse.set(
                  featureFlags
                      .updateFeatureFlag(FLAG, new Patch(true, false, null), sysadmin, sysadmin)
                      .orElseThrow()
                      .isBaselineValue());
            });

    assertTrue(secondResponse.get());
    assertTrue(featureFlags.isFeatureFlagEnabled(FLAG, (User) null));
  }

  @Test
  void finalBaselineChangeWinsWithinOneTransaction() {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored -> {
              featureFlags.updateFeatureFlag(
                  FLAG, new Patch(true, false, null), sysadmin, sysadmin);
              assertFalse(
                  featureFlags
                      .updateFeatureFlag(FLAG, new Patch(false, false, null), sysadmin, sysadmin)
                      .orElseThrow()
                      .isBaselineValue());
            });
    assertFalse(featureFlags.isFeatureFlagEnabled(FLAG, (User) null));
  }

  private void setBaseline(boolean value) {
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                featureFlags.updateFeatureFlag(
                    FLAG, new Patch(value, false, null), sysadmin, sysadmin));
  }
}
