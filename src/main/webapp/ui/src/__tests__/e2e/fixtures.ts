import type { APIRequestContext } from "@playwright/test";
import { test as base, expect, mergeTests, request } from "@playwright/test";
import { DocumentsClient } from "./api/clients/DocumentsClient";
import { env } from "./env";
import { LoginPage } from "./pageObjects/LoginPage";
import { WorkspacePage } from "./pageObjects/WorkspacePage";
import { type AppUser, USERS } from "./users";

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
    await use(pageWorkspace);
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

export const test = mergeTests(uiTest, apiTest);
export { tags } from "./tags";
export { expect };
