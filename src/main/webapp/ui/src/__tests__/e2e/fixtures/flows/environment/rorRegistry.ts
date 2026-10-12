import type { Browser, BrowserContextOptions } from "@playwright/test";
import { RorRegistryClient } from "@/__tests__/e2e/api/clients/RorRegistryClient";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";
import { withSysadminPage } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import { SystemConfigPage } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

export const ROR_TEST_ORGANISATION = { id: "https://ror.org/02mhbdp94", name: "Universidad de Los Andes" };

type RorRegistryFixtures = {
  flowRorRegistry: {
    config: SystemConfigPage;
    client: RorRegistryClient;
  };
  flowLinkedRor: typeof ROR_TEST_ORGANISATION;
};

/** Sets the instance's ROR link for the test and restores the original link afterwards. */
async function withInstanceRor(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  rorId: string,
  use: (sysadmin: { config: SystemConfigPage; client: RorRegistryClient }) => Promise<void>,
): Promise<void> {
  await withSysadminPage(
    browser,
    browserContextOptions,
    async (page) => ({ config: new SystemConfigPage(page), client: new RorRegistryClient(page.request) }),
    async (sysadmin) => {
      const original = await sysadmin.client.linkedRorId();
      await sysadmin.client.setLinkedRorId(rorId);
      try {
        await use(sysadmin);
      } finally {
        await sysadmin.client.setLinkedRorId(original);
      }
    },
  );
}

export const test = apiTest.extend<RorRegistryFixtures>({
  flowRorRegistry: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("flowRorRegistry");
    await withInstanceRor(browser, browserContextOptions, "", use);
  },

  flowLinkedRor: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("flowLinkedRor");
    await withInstanceRor(browser, browserContextOptions, ROR_TEST_ORGANISATION.id, () => use(ROR_TEST_ORGANISATION));
  },
});
