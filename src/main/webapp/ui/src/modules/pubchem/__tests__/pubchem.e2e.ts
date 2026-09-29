import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { tags } from "@/__tests__/e2e/tags";
import type { PubchemDialogComponent } from "./pageObjects/PubchemDialogComponent";

const EXPECTED = {
  name: "Aspirin",
  formula: "C9H8O4",
  cas: "50-78-2",
};
const INTEGRATION_MODE = env.integrationMode;

/** Creates a Basic Document and opens the "Insert from PubChem" dialog on its default field. */
async function openPubchemDialogOnNewDocument(
  pageWorkspace: WorkspacePage,
): Promise<{ docEditor: DocumentEditorPage; dialog: PubchemDialogComponent }> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const dialog = await docEditor.openPubchemDialog();
  return { docEditor, dialog };
}

test.describe(`PubChem integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, I can search PubChem by compound name", async ({ pageWorkspace }) => {
    const { dialog } = await openPubchemDialogOnNewDocument(pageWorkspace);

    await dialog.search("aspirin");

    await expect(dialog.resultCard(EXPECTED.name)).toBeVisible();
    await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.formula)).toBeVisible();
    await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.cas)).toBeVisible();
  });

  test("As a user, I can import a PubChem compound into a document", async ({ page, pageWorkspace }) => {
    const { docEditor, dialog } = await openPubchemDialogOnNewDocument(pageWorkspace);
    await dialog.search("aspirin");

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/chemical/ajax/createChemElement"),
      ),
      dialog.importCompound(EXPECTED.name),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /chemical/ajax/createChemElement failed: ${response.status()} ${response.statusText()}`);
    }

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
  });

  test("As a user, searching PubChem for a nonexistent compound shows no results", async ({ pageWorkspace }) => {
    const { dialog } = await openPubchemDialogOnNewDocument(pageWorkspace);

    await dialog.search("this-does-not-exist-12345");

    await expect(dialog.resultsRegion.getByText("No compounds found", { exact: false })).toBeVisible();
  });

  test("As a user, I can search PubChem by SMILES", async ({ pageWorkspace }) => {
    const { dialog } = await openPubchemDialogOnNewDocument(pageWorkspace);

    await dialog.search("CC(=O)OC1=CC=CC=C1C(=O)O", "SMILES");

    await expect(dialog.resultCard(EXPECTED.name)).toBeVisible();
  });

  test("As a user, I can search PubChem by CAS number", async ({ pageWorkspace }) => {
    const { dialog } = await openPubchemDialogOnNewDocument(pageWorkspace);

    await dialog.search("83-88-5");

    await expect(dialog.resultCard("Riboflavin")).toBeVisible();
  });
});
