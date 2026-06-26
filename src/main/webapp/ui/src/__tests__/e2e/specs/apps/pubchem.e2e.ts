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
      const userCtx = await browser.newContext();
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

    test("Searching for a non-existent compound shows the no-results message", async () => {
      const dialog = await docPage.openPubchemDialog();

      await test.step("When I search for a term with no matches", async () => {
        await dialog.search("xyzzy-nonexistent-12345");
      });

      await test.step("Then the results region shows 'No compounds found'", async () => {
        await expect(dialog.resultsRegion).toContainText(/No compounds found/);
      });

      await test.step("And I close the dialog", async () => {
        await dialog.close();
      });
    });

    test("Searching 'aspirin' by Name/CAS and SMILES returns result cards with accessible names", async () => {
      const dialog = await docPage.openPubchemDialog();

      await test.step("When I search 'aspirin' by Name/CAS", async () => {
        await dialog.search("aspirin", "Name/CAS");
      });

      await test.step("Then a result card named 'Aspirin' is visible", async () => {
        await expect(dialog.resultCard("Aspirin")).toBeVisible();
      });

      await test.step("When I switch to SMILES search type", async () => {
        await dialog.search("CC(=O)Oc1ccccc1C(=O)O", "SMILES");
      });

      await test.step("Then the search type combobox reflects SMILES", async () => {
        await expect(dialog.searchTypeCombobox).toHaveText(/SMILES/);
      });

      await test.step("And a result card is still visible in SMILES mode", async () => {
        await expect(dialog.resultCard("Aspirin")).toBeVisible();
      });

      await test.step("And I close the dialog", async () => {
        await dialog.close();
      });
    });

    test("Selecting a compound and clicking Import Selected inserts it into the document", async () => {
      const dialog = await docPage.openPubchemDialog();

      await test.step("Given a search for 'aspirin' returns the Aspirin compound", async () => {
        await dialog.search("aspirin");
        await expect(dialog.resultCard("Aspirin")).toBeVisible();
      });

      await test.step("When I select the compound and click Import Selected", async () => {
        await dialog.importCompound("Aspirin");
      });

      await test.step("Then the dialog closes", async () => {
        await expect(dialog.root).not.toBeVisible();
      });

      await test.step("And the document field contains the imported structure image", async () => {
        // TinyMCE editor iframes have IDs ending in "_ifr" (e.g. "tinymce_0_ifr").
        // Scoping to that pattern avoids matching unrelated iframes on the page.
        const editorFrame = sharedPage.frameLocator("iframe[id$='_ifr']").first();
        await expect(editorFrame.locator("img")).toBeVisible({ timeout: 10_000 });
      });
    });
  });
