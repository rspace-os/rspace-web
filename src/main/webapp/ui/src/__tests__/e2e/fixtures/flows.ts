import { storageStatePath } from "../authState";
import { env } from "../env";
import { SystemConfigPage } from "../pageObjects/system/SystemConfigPage";
import { SYSADMIN } from "../users";
import { apiTest } from "./api";

type FlowFixtures = {
  flowIgsnConfig: undefined;
  flowSysadminConfig: SystemConfigPage;
};

export const test = apiTest.extend<FlowFixtures>({
  flowIgsnConfig: async ({ clientSysadmin }, use) => {
    await clientSysadmin.configureIgsn({
      provider: "IGSN_DATACITE",
      enabled: "true",
      serverUrl: env.integrationMode === "mock" ? env.mockBackendBaseUrl : env.igsnServerUrl,
      username: env.integrationMode === "mock" ? "mock-igsn-account" : env.igsnAccountId,
      password: env.integrationMode === "mock" ? "mock-igsn-password" : env.igsnPassword,
      repositoryPrefix: env.integrationMode === "mock" ? "10.99999" : env.igsnRepoPrefix,
    });
    if (!(await clientSysadmin.testIgsnConnection())) {
      throw new Error("IGSN provider connection test returned false after configuration");
    }
    await use(undefined);
  },
  flowSysadminConfig: async ({ browser, browserContextOptions }, use) => {
    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    try {
      const page = await ctx.newPage();
      const configPage = new SystemConfigPage(page);
      await configPage.open();
      await use(configPage);
    } finally {
      await ctx.close();
    }
  },
});
