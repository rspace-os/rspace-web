import { storageStatePath } from "@/__tests__/e2e/authState";
import { expect, tags, test } from "@/__tests__/e2e/fixtures";
import { INTEGRATION_MODE } from "@/__tests__/e2e/integrationMode";
import { AppsPage } from "@/__tests__/e2e/pageObjects/apps/AppsPage";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { PubchemDialogComponent } from "./pageObjects/PubchemDialogComponent";

/**
 * Expected compound data — must match src/modules/pubchem/__tests__/pubchemMock/fixtures/.
 * In real mode the live PubChem API must return the same values (drift check).
 */
const EXPECTED = {
  name: "Aspirin",
  formula: "C9H8O4",
  cas: "50-78-2",
};

test.describe
  .serial(`PubChem integration [${INTEGRATION_MODE}] ${tags.APPS}`, () => {
    // Enable the system-level chemistry setting as sysadmin.
    test.beforeAll(async ({ flowSysadminConfig }) => {
      await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
    });

    // Uses the appUser's storageState rather than logging in again — a
    // second concurrent login as the same user racing the project's other
    // specs' `flowLogin` can trip Hibernate's optimistic lock on `User`.
    test.beforeAll(async ({ browser, browserContextOptions, appUser }) => {
      const ctx = await browser.newContext({
        ...browserContextOptions,
        storageState: storageStatePath(appUser.username),
      });
      try {
        const page = await ctx.newPage();
        await new AppsPage(page).setEnabled("Chemistry", true);
      } finally {
        await ctx.close();
      }
    });

    test("Chemistry enabled — 'Insert PubChem Compound' toolbar button appears in a new document", async ({
      flowLogin,
    }) => {
      await test.step("Given I have a new document open with Chemistry enabled", async () => {
        const docEditor = await flowLogin.createBasicDocument();
        await expect(docEditor.pubchemToolbarButton).toBeVisible();
      });
    });

    test("Searching by compound name returns a result with the expected fields", async ({ flowLogin }) => {
      // Chemistry enabled by beforeAll for this user (serial suite).
      let dialog!: PubchemDialogComponent;

      await test.step("Given I have a new document open with Chemistry enabled", async () => {
        const docEditor = await flowLogin.createBasicDocument();
        dialog = await docEditor.openPubchemDialog();
      });

      await test.step("When I search for 'aspirin'", async () => {
        await dialog.search("aspirin");
      });

      await test.step("Then the result card shows the compound name, formula, and CAS number", async () => {
        await expect(dialog.resultCard(EXPECTED.name)).toBeVisible();
        await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.formula)).toBeVisible();
        await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.cas)).toBeVisible();
      });
    });

    test("Importing a compound inserts a chemistry element into the document", async ({ page, flowLogin }) => {
      let docEditor!: DocumentEditorPage;
      let dialog!: PubchemDialogComponent;

      await test.step("Given I have searched for 'aspirin' in the PubChem dialog", async () => {
        docEditor = await flowLogin.createBasicDocument();
        dialog = await docEditor.openPubchemDialog();
        await dialog.search("aspirin");
      });

      await test.step("When I import the selected compound", async () => {
        const [response] = await Promise.all([
          page.waitForResponse((r) => r.url().includes("/chemical/ajax/createChemElement")),
          dialog.importCompound(EXPECTED.name),
        ]);
        if (!response.ok()) {
          throw new Error(
            `POST /chemical/ajax/createChemElement failed: ${response.status()} ${response.statusText()}`,
          );
        }
      });

      await test.step("Then a chemistry element appears in the document field", async () => {
        const field = await docEditor.getField("New List of Materials");
        await expect(field.chemElement).toBeVisible();
      });
    });
  });
