import type { APIRequestContext } from "@playwright/test";
import { test as base, expect, mergeTests, request } from "@playwright/test";
import { DocumentsClient } from "./api/clients/DocumentsClient";
import { env } from "./env";
import { DocumentEditorPage } from "./pageObjects/DocumentEditorPage";
import { DocumentPage } from "./pageObjects/DocumentPage";
import { LoginPage } from "./pageObjects/LoginPage";
import { SystemConfigPage } from "./pageObjects/SystemConfigPage";
import { WorkspacePage } from "./pageObjects/WorkspacePage";
import { type AppUser, SYSADMIN, USERS } from "./users";

export type { AppUser };

/**
 * `appUser` is a Playwright test option (not a setup fixture) — set per
 * project in `playwright-e2e.config.ts` so each browser project logs in as a
 * different seed user. This prevents shared-state collisions when browser
 * projects run in parallel against the same backend.
 */
export type E2EOptions = { appUser: AppUser };

/**
 * Fixture names are prefixed by type so the prefix tells you where the
 * implementation lives:
 *   page*      → pageObjects/
 *   component* → components/
 *   client*    → api/clients/
 *   flow*      → multi-step setup returning a result (not a bare page object)
 *
 * Use `flowLogin` when being logged in is a precondition. Use bare
 * `pageLogin`/`pageWorkspace` when the login flow itself is under test — don't
 * hide the thing-under-test inside a fixture.
 */
const uiTest = base.extend<
  E2EOptions & {
    pageLogin: LoginPage;
    pageWorkspace: WorkspacePage;
    pageDocument: DocumentPage;
    pageDocumentEditor: DocumentEditorPage;
    flowLogin: WorkspacePage;
  }
>({
  appUser: [USERS.user1a, { option: true }],
  pageLogin: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
  pageWorkspace: async ({ page }, use) => {
    await use(new WorkspacePage(page));
  },
  pageDocument: async ({ page }, use) => {
    await use(new DocumentPage(page));
  },
  pageDocumentEditor: async ({ page }, use) => {
    await use(new DocumentEditorPage(page));
  },
  /**
   * Logs in via the UI form. Uses `appUser` (set per project in config) so
   * each browser project authenticates as a distinct seed user, avoiding
   * parallel-run state collisions.
   *
   * Worth replacing with `storageState` reuse once login becomes the
   * bottleneck.
   */
  flowLogin: async ({ pageLogin, pageWorkspace, appUser }, use) => {
    await pageLogin.open();
    await pageLogin.login(appUser.username, appUser.password);
    const loaded = await pageWorkspace.isLoaded();
    if (!loaded) {
      throw new Error(
        `flowLogin: workspace did not load after login as '${appUser.username}'. ` +
          "Login may have failed or the server returned an unexpected page.",
      );
    }
    await use(pageWorkspace);
  },
});

/**
 * `flowSysadminConfig` — multi-step setup fixture following the `flow*` naming
 * convention. Creates an isolated browser context, logs in as sysadmin, opens
 * SystemConfigPage, and delivers a ready-to-use instance. Context is torn down
 * after the consumer (test or beforeAll) returns.
 *
 * Use when sysadmin system-level state is a precondition (e.g. enabling
 * chemistry.available before an Apps integration test).
 */
const sysadminFixtures = base.extend<{
  flowSysadminConfig: SystemConfigPage;
}>({
  flowSysadminConfig: async ({ browser, browserName }, use) => {
    // Manually created contexts don't inherit the project-level ignoreHTTPSErrors.
    const ctx = await browser.newContext({ ignoreHTTPSErrors: browserName === "webkit" });
    try {
      const page = await ctx.newPage();
      const loginPage = new LoginPage(page);
      await loginPage.open();
      await loginPage.login(SYSADMIN.username, SYSADMIN.password);
      await page.waitForURL((url) => !url.pathname.includes("/login"));
      const configPage = new SystemConfigPage(page);
      await configPage.open();
      await use(configPage);
    } finally {
      await ctx.close();
    }
  },
});

const apiTest = base.extend<{
  apiContext: APIRequestContext;
  clientDocuments: DocumentsClient;
}>({
  // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture arg
  apiContext: async ({}, use) => {
    const context = await request.newContext({ baseURL: env.baseURL });
    await use(context);
    await context.dispose();
  },
  clientDocuments: async ({ apiContext }, use) => {
    await use(new DocumentsClient(apiContext, env.testApiKey));
  },
});

export const test = mergeTests(uiTest, apiTest, sysadminFixtures);
export { tags } from "./tags";
export { expect };
