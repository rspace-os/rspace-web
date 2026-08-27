import type { Browser, BrowserContextOptions } from "@playwright/test";
import { createDynamicUser } from "../createDynamicUser";
import { LoginPage } from "../pageObjects/auth/LoginPage";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { DYNAMIC_USER_PASSWORD } from "../testData";
import { test } from "./flows";

type CreatableRole = "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN";

async function loginInNewContext(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  username: string,
): Promise<{ workspace: WorkspacePage; close: () => Promise<void> }> {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  const page = await ctx.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.login(username, DYNAMIC_USER_PASSWORD);
  await page.waitForURL((url) => !url.pathname.includes("/login"));
  return { workspace: new WorkspacePage(page), close: () => ctx.close() };
}

type DynamicUserFixtures = {
  flowCreateUser: (
    role: CreatableRole,
    namePrefix?: string,
  ) => Promise<{ username: string; apiKey: string; workspace: WorkspacePage }>;
};

export const dynamicUserTest = test.extend<DynamicUserFixtures>({
  appUser: async ({ clientSysadmin }, use) => {
    const { username, apiKey } = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eDynUser", "DynamicUser");
    await use({ username, password: DYNAMIC_USER_PASSWORD, apiKey, roles: ["ROLE_PI", "ROLE_USER"] });
  },
  storageState: async ({ appUser, browser, browserContextOptions }, use) => {
    // Manual contexts must set baseURL and clear the project's seed-user storage state.
    const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
    try {
      const page = await ctx.newPage();
      const loginPage = new LoginPage(page);
      await loginPage.open();
      await loginPage.login(appUser.username, appUser.password);
      await page.waitForURL((url) => url.pathname === "/workspace");
      const workspace = new WorkspacePage(page);
      if (!(await workspace.isLoaded())) {
        throw new Error(`Workspace did not load after authenticating dynamic user '${appUser.username}'.`);
      }
      await use(await ctx.storageState());
    } finally {
      await ctx.close();
    }
  },

  flowCreateUser: async ({ clientSysadmin, browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (role, namePrefix = "e2eDynUser2") => {
        const { username, apiKey } = await createDynamicUser(clientSysadmin, role, namePrefix);
        const { workspace, close } = await loginInNewContext(browser, browserContextOptions, username);
        closers.push(close);
        return { username, apiKey, workspace };
      });
    } finally {
      const results = await Promise.allSettled(closers.map((close) => close()));
      for (const result of results) {
        if (result.status === "rejected") {
          console.error("Failed to close a flowCreateUser browser context during teardown:", result.reason);
        }
      }
    }
  },
});
