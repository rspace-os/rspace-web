import { env } from "@/__tests__/e2e/env";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import type { SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

type SampleRequestsFixtures = {
  /** Ensures inventory.sampleRequests.available is ALLOWED for the test's duration, restoring it after. */
  flowSampleRequestsAvailable: undefined;
  /**
   * Ensures inventory.operations.available is DENIED for the test's duration, restoring it after.
   * Sample-request tests not specifically exercising the Operations Wizard route (the
   * "wizard" radio in Choose Sample to Prepare) should pull this in so they run the same way
   * regardless of whichever value a previous or concurrent test left that property at.
   */
  flowOperationsNotAvailable: undefined;
  /**
   * Ensures inventory.operations.available is ALLOWED for the test's duration, restoring it
   * after. Pulled in by sample-request tests that specifically exercise the Operations Wizard
   * route (the "wizard" radio in Choose Sample to Prepare, or the Prepare Sample button that
   * replaces the direct Transfer Sample button once this property is ALLOWED).
   */
  flowOperationsAvailable: undefined;
};

export const test = sysadminSessionTest.extend<SampleRequestsFixtures>({
  flowSampleRequestsAvailable: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowSampleRequestsAvailable");
    const original = (
      await flowSysadminConfig.getSetting("inventory.sampleRequests.available")
    ).trim() as SystemPropertyValue;
    try {
      await flowSysadminConfig.ensureSetting("inventory.sampleRequests.available", "ALLOWED");
      await use(undefined);
    } finally {
      await flowSysadminConfig.ensureSetting("inventory.sampleRequests.available", original);
    }
  },

  flowOperationsNotAvailable: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowOperationsNotAvailable");
    const original = (
      await flowSysadminConfig.getSetting("inventory.operations.available")
    ).trim() as SystemPropertyValue;
    try {
      await flowSysadminConfig.ensureSetting("inventory.operations.available", "DENIED");
      await use(undefined);
    } finally {
      await flowSysadminConfig.ensureSetting("inventory.operations.available", original);
    }
  },

  flowOperationsAvailable: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowOperationsAvailable");
    const original = (
      await flowSysadminConfig.getSetting("inventory.operations.available")
    ).trim() as SystemPropertyValue;
    try {
      await flowSysadminConfig.ensureSetting("inventory.operations.available", "ALLOWED");
      await use(undefined);
    } finally {
      await flowSysadminConfig.ensureSetting("inventory.operations.available", original);
    }
  },
});
