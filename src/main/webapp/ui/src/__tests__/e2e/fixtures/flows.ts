import type { Browser, BrowserContext, BrowserContextOptions, Page } from "@playwright/test";
import type { SysadminClient } from "../api/clients/SysadminClient";
import { storageStatePath } from "../authState";
import { env } from "../env";
import { InventoryPage } from "../pageObjects/inventory/InventoryPage";
import { PublicDocumentPage } from "../pageObjects/myrspace/PublicDocumentPage";
import { SystemConfigPage, type SystemPropertyValue } from "../pageObjects/system/SystemConfigPage";
import { SYSADMIN } from "../users";
import { apiTest } from "./api";

type FlowFixtures = {
  flowIgsnConfig: undefined;
  flowPidinstDataciteConfig: undefined;
  flowPidinstB2instConfig: undefined;
  flowSysadminConfig: SystemConfigPage;
  flowSysadminInventory: InventoryPage;
  flowPublicSharing: undefined;
  flowOpenAnonymousDocument: (href: string) => Promise<PublicDocumentPage>;
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

function mockOrReal<T>(mock: T, real: T): T {
  return env.integrationMode === "mock" ? mock : real;
}

async function configureDataciteProvider(
  clientSysadmin: SysadminClient,
  provider: "IGSN_DATACITE" | "PIDINST_DATACITE",
): Promise<void> {
  const account = {
    enabled: "true" as const,
    ...mockOrReal(
      {
        serverUrl: env.mockBackendBaseUrl,
        username: "mock-igsn-account",
        password: "mock-igsn-password",
        repositoryPrefix: "10.99999",
      },
      {
        serverUrl: env.igsnServerUrl,
        username: env.igsnAccountId,
        password: env.igsnPassword,
        repositoryPrefix: env.igsnRepoPrefix,
      },
    ),
  };

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
      ...mockOrReal(
        { serverUrl: env.mockBackendBaseUrl, username: "mock-b2inst-community", password: "mock-b2inst-token" },
        {
          serverUrl: env.pidinstB2instServerUrl,
          username: env.pidinstB2instCommunityId,
          password: env.pidinstB2instToken,
        },
      ),
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

  flowPublicSharing: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowPublicSharing");
    const settingNames = ["public_sharing", "publicdocs_allow_seo"] as const;
    const originalValues = new Map<string, SystemPropertyValue>();
    for (const name of settingNames) {
      originalValues.set(name, (await flowSysadminConfig.getSetting(name)).trim() as SystemPropertyValue);
    }
    try {
      await flowSysadminConfig.ensureSettings({
        public_sharing: "ALLOWED",
        publicdocs_allow_seo: "ALLOWED",
      });
      await use(undefined);
    } finally {
      for (const [name, value] of originalValues) {
        await flowSysadminConfig.ensureSetting(name, value);
      }
    }
  },

  flowOpenAnonymousDocument: async ({ browser, browserContextOptions }, use) => {
    const contexts: BrowserContext[] = [];
    try {
      await use(async (href) => {
        const context = await browser.newContext({ ...browserContextOptions, storageState: undefined });
        contexts.push(context);
        const publicPage = new PublicDocumentPage(await context.newPage());
        await publicPage.openAt(href);
        return publicPage;
      });
    } finally {
      await Promise.all(contexts.map((context) => context.close()));
    }
  },
});
