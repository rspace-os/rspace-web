import type { Page } from "@playwright/test";
import { expect, tags, test } from "@/__tests__/e2e/fixtures";
import { AppsPage } from "@/__tests__/e2e/pageObjects/AppsPage";
import { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/DocumentEditorPage";
import { LoginPage } from "@/__tests__/e2e/pageObjects/LoginPage";

// ---------------------------------------------------------------------------
// Serial suite: each test depends on state set by the previous one.
// beforeAll handles all shared setup so the five tests can share one page.
// ---------------------------------------------------------------------------

test.describe
  .serial(`PubChem integration ${tags.APPS}`, () => {
    // beforeAll does sysadmin login + config + user login + app toggle + document creation.
    // 30s default is too tight for slow engines (WebKit especially). 120s covers the full chain.
    test.describe.configure({ timeout: 120_000 });

    let sharedPage: Page;
    let docPage: DocumentEditorPage;
    let documentUrl: string;

    test.beforeAll(async ({ browser, appUser, flowSysadminConfig }) => {
      // 1. Sysadmin: ensure chemistry.available = ALLOWED so Chemistry appears
      //    in the user's Apps page. Idempotent — skips the write if already set.
      await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");

      // 2. User: log in and disable Chemistry so test 1 starts from "disabled".
      // ignoreHTTPSErrors mirrors the project-level webkit config. Without it,
      // the manually created context doesn't inherit that option and WebKit's
      // older TLS stack rejects cipher suites.
      const userCtx = await browser.newContext({ ignoreHTTPSErrors: true });
      sharedPage = await userCtx.newPage();
      const userLogin = new LoginPage(sharedPage);
      await userLogin.open();
      await userLogin.login(appUser.username, appUser.password);
      const apps = new AppsPage(sharedPage);
      await apps.setEnabled("Chemistry", false);

      // 3. Create a new basic document so we have an editor to work with.
      await sharedPage.goto("/workspace");
      // creating a document should be via api
      await sharedPage.getByTestId("create-btn").click();
      await sharedPage.getByTestId("create-btn-basic-document").click();
      await sharedPage.waitForURL("**/workspace/editor/structuredDocument/**");
      documentUrl = sharedPage.url();

      docPage = new DocumentEditorPage(sharedPage);
      await docPage.isLoaded();
    });

    test.afterAll(async () => {
      await sharedPage?.context().close();
    });

    test("After enabling Chemistry, the 'Insert PubChem Compound' toolbar button appears", async () => {
      await test.step("When I enable Chemistry in my Apps settings", async () => {
        const apps = new AppsPage(sharedPage);
        await apps.setEnabled("Chemistry", true);
      });

      await test.step("And I navigate back to the document editor", async () => {
        await sharedPage.goto(documentUrl);
        await docPage.isLoaded();
      });

      await test.step("Then the PubChem toolbar button is visible", async () => {
        await expect(docPage.pubchemToolbarButton).toBeVisible();
      });
    });
  });
