import { env } from "@/__tests__/e2e/env";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import type { SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

type OperationsFixtures = {
  /** Ensures inventory.operations.available is DENIED for the test's duration, restoring it after. */
  flowOperationsDenied: undefined;
};

export const test = sysadminSessionTest.extend<OperationsFixtures>({
  flowOperationsDenied: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowOperationsDenied");
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
});
