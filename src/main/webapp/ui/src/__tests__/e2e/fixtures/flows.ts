import type { Browser, BrowserContextOptions, Page } from "@playwright/test";
import type { SysadminClient } from "../api/clients/SysadminClient";
import { storageStatePath } from "../authState";
import { env } from "../env";
import { InventoryPage } from "../pageObjects/inventory/InventoryPage";
import { SystemConfigPage } from "../pageObjects/system/SystemConfigPage";
import { SYSADMIN } from "../users";
import { apiTest } from "./api";

type FlowFixtures = {
  flowIgsnConfig: undefined;
  flowPidinstDataciteConfig: undefined;
  flowPidinstB2instConfig: undefined;
  flowSysadminConfig: SystemConfigPage;
  flowSysadminInventory: InventoryPage;
};

async function withSysadminPage<T>(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  open: (page: Page) => Promise<T>,
  use: (pageObject: T) => Promise<void>,
): Promise<void> {
  const ctx = await browser.newContext({
    ...browserContextOptions,
    storageState: storageStatePath(SYSADMIN.username),
  });
  try {
    const page = await ctx.newPage();
    await use(await open(page));
  } finally {
    await ctx.close();
  }
}

async function configureDataciteProvider(
  clientSysadmin: SysadminClient,
  provider: "IGSN_DATACITE" | "PIDINST_DATACITE",
): Promise<void> {
  const serverUrl = env.integrationMode === "mock" ? env.mockBackendBaseUrl : env.igsnServerUrl;
  const username = env.integrationMode === "mock" ? "mock-igsn-account" : env.igsnAccountId;
  const password = env.integrationMode === "mock" ? "mock-igsn-password" : env.igsnPassword;
  const repositoryPrefix = env.integrationMode === "mock" ? "10.99999" : env.igsnRepoPrefix;
  const account = { enabled: "true" as const, serverUrl, username, password, repositoryPrefix };

  if (provider === "IGSN_DATACITE") {
    await clientSysadmin.configureIgsn({ provider, ...account });
    if (!(await clientSysadmin.testIgsnConnection())) {
      throw new Error("IGSN provider connection test returned false after configuration");
    }
  } else {
    await clientSysadmin.configurePidinst({ provider, ...account });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST DataCite provider connection test returned false after configuration");
    }
  }
}

export const test = apiTest.extend<FlowFixtures>({
  flowIgsnConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "IGSN_DATACITE");
    await use(undefined);
  },

  flowPidinstDataciteConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "PIDINST_DATACITE");
    await use(undefined);
  },

  flowPidinstB2instConfig: async ({ clientSysadmin }, use) => {
    await clientSysadmin.configurePidinst({
      provider: "PIDINST_B2INST",
      enabled: "true",
      serverUrl: env.integrationMode === "mock" ? env.mockBackendBaseUrl : env.pidinstB2instServerUrl,
      username: env.integrationMode === "mock" ? "mock-b2inst-community" : env.pidinstB2instCommunityId,
      password: env.integrationMode === "mock" ? "mock-b2inst-token" : env.pidinstB2instToken,
      repositoryPrefix: "",
    });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST B2INST connection test returned false after configuration");
    }
    await use(undefined);
  },
  flowSysadminConfig: async ({ browser, browserContextOptions }, use) =>
    withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => {
        const configPage = new SystemConfigPage(page);
        await configPage.open();
        return configPage;
      },
      use,
    ),

  flowSysadminInventory: async ({ browser, browserContextOptions }, use) =>
    withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => {
        const inventory = new InventoryPage(page);
        await inventory.open();
        return inventory;
      },
      use,
    ),
});
