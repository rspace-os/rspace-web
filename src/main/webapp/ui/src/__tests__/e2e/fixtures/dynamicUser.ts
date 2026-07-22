import type { Browser, BrowserContextOptions } from "@playwright/test";
import type { SysadminClient } from "../api/clients/SysadminClient";
import { LoginPage } from "../pageObjects/auth/LoginPage";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique } from "../testData";
import { test } from "./flows";

const DYNAMIC_USER_PASSWORD = "Passw0rd!23";

type CreatableRole = "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN";

async function createDynamicUser(
  clientSysadmin: SysadminClient,
  role: CreatableRole,
  namePrefix: string,
): Promise<{ username: string; password: string; apiKey: string }> {
  const username = alphaNumericUnique(namePrefix);
  const apiKey = alphaNumericUnique(`${namePrefix}Key`).slice(0, 32);
  await clientSysadmin.createUser({
    username,
    password: DYNAMIC_USER_PASSWORD,
    email: `${username}@example.com`,
    firstName: "E2E",
    lastName: namePrefix,
    role,
    apiKey,
  });
  return { username, password: DYNAMIC_USER_PASSWORD, apiKey };
}

async function loginInNewContext(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  { username, password }: { username: string; password: string },
): Promise<{ workspace: WorkspacePage; close: () => Promise<void> }> {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  const page = await ctx.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.login(username, password);
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
    const username = alphaNumericUnique("e2eDynUser");
    const apiKey = alphaNumericUnique("e2eDynUserKey").slice(0, 32);
    await clientSysadmin.createUser({
      username,
      password: DYNAMIC_USER_PASSWORD,
      email: `${username}@example.com`,
      firstName: "E2E",
      lastName: "DynamicUser",
      role: "ROLE_PI",
      apiKey,
    });
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
        const user = await createDynamicUser(clientSysadmin, role, namePrefix);
        const { workspace, close } = await loginInNewContext(browser, browserContextOptions, user);
        closers.push(close);
        return { username: user.username, apiKey: user.apiKey, workspace };
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
