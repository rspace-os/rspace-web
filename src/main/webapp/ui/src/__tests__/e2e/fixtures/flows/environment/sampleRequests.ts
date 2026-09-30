import { env } from "@/__tests__/e2e/env";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import type { SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

type SampleRequestsFixtures = {
  /** Ensures sampleRequests.available is ALLOWED for the test's duration, restoring it after. */
  flowSampleRequestsAvailable: undefined;
};

export const test = sysadminSessionTest.extend<SampleRequestsFixtures>({
  flowSampleRequestsAvailable: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowSampleRequestsAvailable");
    const original = (await flowSysadminConfig.getSetting("sampleRequests.available")).trim() as SystemPropertyValue;
    try {
      await flowSysadminConfig.ensureSetting("sampleRequests.available", "ALLOWED");
      await use(undefined);
    } finally {
      await flowSysadminConfig.ensureSetting("sampleRequests.available", original);
    }
  },
});
