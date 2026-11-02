import { env } from "@/__tests__/e2e/env";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import type { SystemConfigPage, SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

const OPERATIONS_FLAG = "inventory.operations.available";

type InventoryOperationsFixtures = {
  flowOperationsEnabled: undefined;
  flowOperationsDisabled: undefined;
};

/** The flag is read at page load, so a test must request this fixture before opening Inventory. */
async function withOperationsFlag(
  config: SystemConfigPage,
  value: SystemPropertyValue,
  fixtureName: string,
  use: (r: undefined) => Promise<void>,
): Promise<void> {
  env.assertGlobalMutationsAllowed(fixtureName);
  const original = (await config.getSetting(OPERATIONS_FLAG)).trim() as SystemPropertyValue;
  try {
    await config.ensureSetting(OPERATIONS_FLAG, value);
    await use(undefined);
  } finally {
    await config.ensureSetting(OPERATIONS_FLAG, original);
  }
}

export const test = sysadminSessionTest.extend<InventoryOperationsFixtures>({
  flowOperationsEnabled: async ({ flowSysadminConfig }, use) =>
    withOperationsFlag(flowSysadminConfig, "ALLOWED", "flowOperationsEnabled", use),
  flowOperationsDisabled: async ({ flowSysadminConfig }, use) =>
    withOperationsFlag(flowSysadminConfig, "DENIED", "flowOperationsDisabled", use),
});
