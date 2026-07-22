import { LoginPage } from "../pageObjects/auth/LoginPage";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique } from "../testData";
import { test } from "./flows";

const DYNAMIC_USER_PASSWORD = "Passw0rd!23";

export const dynamicUserTest = test.extend<object>({
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
    const page = await ctx.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(appUser.username, appUser.password);
    await page.waitForURL((url) => url.pathname === "/workspace");
    const workspace = new WorkspacePage(page);
    if (!(await workspace.isLoaded())) {
      throw new Error(`Workspace did not load after authenticating dynamic user '${appUser.username}'.`);
    }
    const state = await ctx.storageState();
    await ctx.close();
    await use(state);
  },
});
