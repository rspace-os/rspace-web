import type { APIRequestContext, BrowserContextOptions } from "@playwright/test";
import { test as base, expect, request } from "@playwright/test";
import { DocumentsClient } from "./api/clients/DocumentsClient";
import { storageStatePath } from "./authState";
import { env } from "./env";
import { DocumentEditorPage } from "./pageObjects/DocumentEditorPage";
import { DocumentPage } from "./pageObjects/DocumentPage";
import { InventoryPage } from "./pageObjects/InventoryPage";
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
    browserContextOptions: BrowserContextOptions;
    pageLogin: LoginPage;
    pageWorkspace: WorkspacePage;
    pageDocument: DocumentPage;
    pageDocumentEditor: DocumentEditorPage;
    pageInventory: InventoryPage;
    flowLogin: WorkspacePage;
  }
>({
  appUser: [USERS.user1a, { option: true }],
  /**
   * Options to pass to `browser.newContext()` when specs create a secondary
   * browser context (e.g. beforeAll sysadmin setup). Centralises the WebKit
   * TLS workaround so call sites don't repeat the condition.
   *
   * WebKit bundled in Playwright uses an older TLS stack that rejects certain
   * cipher suites. `ignoreHTTPSErrors: true` bypasses this for webkit only.
   */
  browserContextOptions: async ({ browserName }, use) => {
    await use({ ignoreHTTPSErrors: browserName === "webkit" });
  },
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
  pageInventory: async ({ page }, use) => {
    await use(new InventoryPage(page));
  },
  /**
   * Delivers a loaded Workspace for an already-authenticated session. The
   * browser project's `storageState` (harvested once by the `setup` project
   * — see `auth.setup.ts`) carries the `appUser` session, so this only opens
   * the workspace and confirms it loaded; it does not drive the login form.
   *
   * If the workspace fails to load, the storageState session is missing or
   * stale — the usual cause is running a spec directly with a `--project`
   * filter that excludes `setup`.
   */
  flowLogin: async ({ pageWorkspace, appUser }, use) => {
    await pageWorkspace.open();
    const loaded = await pageWorkspace.isLoaded();
    if (!loaded) {
      throw new Error(
        `flowLogin: workspace did not load for '${appUser.username}'. ` +
          "The storageState session may be missing or stale — ensure the 'setup' project ran.",
      );
    }
    await use(pageWorkspace);
  },
});

const apiTest = uiTest.extend<{
  apiContext: APIRequestContext;
  clientDocuments: DocumentsClient;
}>({
  // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture arg
  apiContext: async ({}, use) => {
    const context = await request.newContext({ baseURL: env.baseURL });
    await use(context);
    await context.dispose();
  },
  clientDocuments: async ({ apiContext, appUser }, use) => {
    await use(new DocumentsClient(apiContext, appUser.apiKey));
  },
});

/**
 * `flowSysadminConfig` — multi-step setup fixture following the `flow*` naming
 * convention. Creates an isolated browser context authenticated as sysadmin
 * (via the storageState harvested by `auth.setup.ts`), opens SystemConfigPage,
 * and delivers a ready-to-use instance. Context is torn down after the
 * consumer (test or beforeAll) returns.
 *
 * Use when sysadmin system-level state is a precondition (e.g. enabling
 * chemistry.available before an Apps integration test).
 */
export const test = apiTest.extend<{
  flowSysadminConfig: SystemConfigPage;
}>({
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

export { tags } from "./tags";
export { expect };
