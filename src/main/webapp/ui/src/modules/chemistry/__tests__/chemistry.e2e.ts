import { readFileSync } from "node:fs";
import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath } from "@/__tests__/e2e/testData";
import type { ChemistrySearchDialogComponent } from "@/modules/chemistry/__tests__/pageObjects/ChemistrySearchDialogComponent";
import type { PubchemDialogComponent } from "@/modules/pubchem/__tests__/pageObjects/PubchemDialogComponent";

const INTEGRATION_MODE = env.integrationMode;

const ASPIRIN_SMILES = "CC(=O)Oc1ccccc1C(=O)O";
const ASPIRIN = {
  name: "Aspirin",
  formula: "C9H8O4",
};

const CHEMDRAW_CDX = fixturePath(import.meta.url, "fixtures/Fluorescein1.cdx");
const MRV_STRUCTURE_A = fixturePath(import.meta.url, "fixtures/marvinjs_structure_a.mrv");
const MRV_STRUCTURE_AB = fixturePath(import.meta.url, "fixtures/marvinjs_structure_ab.mrv");

/** Creates a Basic Document and inserts aspirin into its "New List of Materials" field via Ketcher. */
async function insertAspirinViaKetcher(
  pageWorkspace: WorkspacePage,
  page: Page,
): Promise<{ docEditor: DocumentEditorPage; docId: number }> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const docId = Number(new URL(page.url()).pathname.split("/").pop());
  const ketcherDialog = await docEditor.openKetcherDialog();
  await ketcherDialog.setMoleculeFromSmiles(ASPIRIN_SMILES);
  await ketcherDialog.insert();
  return { docEditor, docId };
}

/** Inserts the Fluorescein1.cdx Gallery fixture into a document's "New List of Materials" field. */
async function insertFluoresceinViaGallery(docEditor: DocumentEditorPage): Promise<void> {
  const picker = await docEditor.openGalleryPicker();
  await picker.goToSection("Chemistry");
  await picker.uploadFile(CHEMDRAW_CDX, "Fluorescein1.cdx");
  await picker.selectItem("Fluorescein1.cdx");
  await picker.add();
}

/** Opens Chemical Search, runs an EXACT search for aspirin, and returns the open results dialog. */
async function searchExactForAspirin(pageWorkspace: WorkspacePage): Promise<ChemistrySearchDialogComponent> {
  await pageWorkspace.open();
  const searchDialog = await pageWorkspace.openChemicalSearch();
  await searchDialog.setSearchType("EXACT");
  await searchDialog.setMoleculeFromSmiles(ASPIRIN_SMILES);
  await searchDialog.search();
  return searchDialog;
}

/** Imports the selected PubChem compound, waiting on the resulting chemical-creation request. */
async function importPubchemCompoundAndWait(
  page: Page,
  dialog: PubchemDialogComponent,
  compoundName: string,
): Promise<void> {
  const [createResponse] = await Promise.all([
    page.waitForResponse(
      (r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/chemical/ajax/createChemElement"),
    ),
    dialog.importCompound(compoundName),
  ]);
  if (!createResponse.ok()) {
    throw new Error(
      `POST /chemical/ajax/createChemElement failed: ${createResponse.status()} ${createResponse.statusText()}`,
    );
  }
}

test.describe(`Chemistry service [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, converting a SMILES structure returns a real MOL block from the chemistry service", async ({
    page,
  }) => {
    const response = await page.request.post("/chemical/ajax/convert", {
      data: { structure: ASPIRIN_SMILES, inputFormat: "smiles" },
    });
    expect(response.ok()).toBe(true);

    const converted = await response.json();
    expect(converted.errorMessage).toBeFalsy();
    expect(converted.structure).toContain("M  END");
  });

  test("As a user, importing a compound renders a real chemistry-service image, not an empty placeholder", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openPubchemDialog();
    await dialog.search("aspirin");
    await importPubchemCompoundAndWait(page, dialog, ASPIRIN.name);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();

    const imageSrc = await field.chemistry.chemElement.getAttribute("src");
    if (!imageSrc) {
      throw new Error("Chem element image has no src attribute");
    }

    const imageResponse = await page.request.get(imageSrc);
    expect(imageResponse.ok()).toBe(true);
    const bytes = await imageResponse.body();
    expect(bytes.length).toBeGreaterThan(100);
  });

  test("As a user, inserting a chemical structure via the Ketcher editor renders a real chemistry-service image, not an empty placeholder", async ({
    page,
    pageWorkspace,
  }) => {
    const { docEditor } = await insertAspirinViaKetcher(pageWorkspace, page);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();

    const imageSrc = await field.chemistry.chemElement.getAttribute("src");
    if (!imageSrc) {
      throw new Error("Chem element image has no src attribute");
    }

    const imageResponse = await page.request.get(imageSrc);
    expect(imageResponse.ok()).toBe(true);
    const bytes = await imageResponse.body();
    expect(bytes.length).toBeGreaterThan(100);
  });

  test("As a user, I can insert a chemistry file from the Gallery into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    await insertFluoresceinViaGallery(docEditor);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
  });

  test("As a user, viewing a Gallery-inserted chemical structure opens the Ketcher viewer in read-only mode", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    await insertFluoresceinViaGallery(docEditor);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();

    const viewerDialog = await field.chemistry.openKetcherViewer();
    await expect(page.getByRole("dialog", { name: "Ketcher Chemical Viewer (Read-Only)" })).toBeVisible();
    expect(await viewerDialog.isAtomToolEnabled("C")).toBe(false);
  });

  test("As a user, I can reopen an inserted chemical structure and cancel out of Ketcher without changing it", async ({
    pageWorkspace,
    page,
  }) => {
    const { docEditor } = await insertAspirinViaKetcher(pageWorkspace, page);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
    const srcBefore = await field.chemistry.chemElement.getAttribute("src");

    const editDialog = await field.chemistry.openKetcherEditDialog();
    await editDialog.cancel();

    const srcAfter = await field.chemistry.chemElement.getAttribute("src");
    expect(srcAfter).toBe(srcBefore);
  });

  test("As a user, I can upload a structure file directly into Ketcher's own canvas and insert it", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const ketcherDialog = await docEditor.openKetcherDialog();
    await ketcherDialog.uploadStructureFile(MRV_STRUCTURE_A);
    await ketcherDialog.insert();

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
  });

  test("As a user, editing an inserted chemical structure in Ketcher and adding an atom changes its formula", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const ketcherDialog = await docEditor.openKetcherDialog();
    await ketcherDialog.setMoleculeFromSmiles(ASPIRIN_SMILES);
    const formulaBefore = await ketcherDialog.getCalculatedFormula();
    await ketcherDialog.insert();

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();

    const editDialog = await field.chemistry.openKetcherEditDialog();
    await editDialog.selectAtomTool("C");
    await editDialog.clickCanvasAt({ x: 30, y: 30 });
    const formulaAfter = await editDialog.getCalculatedFormula();
    expect(formulaAfter).not.toBe(formulaBefore);
    await editDialog.cancel();
  });

  test("As a user, I can find a document by its exact chemical structure via Chemical Search", async ({
    pageWorkspace,
    page,
  }) => {
    const { docEditor } = await insertAspirinViaKetcher(pageWorkspace, page);
    await docEditor.saveAndView();

    const searchDialog = await searchExactForAspirin(pageWorkspace);

    expect(await searchDialog.resultCount()).toBeGreaterThan(0);
  });

  test("As a user, a substructure search finds a document containing that fragment", async ({
    pageWorkspace,
    page,
  }) => {
    const { docEditor } = await insertAspirinViaKetcher(pageWorkspace, page);
    await docEditor.saveAndView();

    await pageWorkspace.open();
    const searchDialog = await pageWorkspace.openChemicalSearch();
    await searchDialog.setSearchType("SUBSTRUCTURE");
    await searchDialog.setMoleculeFromSmiles("c1ccccc1"); // benzene ring, a substructure of aspirin
    await searchDialog.search();

    expect(await searchDialog.resultCount()).toBeGreaterThan(0);
  });

  test("As a user, drawing multiple molecules in Chemical Search shows a validation warning instead of searching", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const searchDialog = await pageWorkspace.openChemicalSearch();
    await searchDialog.draw.uploadStructureFile(MRV_STRUCTURE_AB);

    await searchDialog.searchExpectingMultipleMoleculesWarning();
  });

  test("As a user, the same structure inserted into two documents (via Ketcher and via PubChem) is found twice by Chemical Search", async ({
    page,
    pageWorkspace,
  }) => {
    const { docEditor: firstDoc } = await insertAspirinViaKetcher(pageWorkspace, page);
    await firstDoc.saveAndView();

    await pageWorkspace.open();
    const secondDoc = await pageWorkspace.createBasicDocument();
    const pubchemDialog = await secondDoc.openPubchemDialog();
    await pubchemDialog.search(ASPIRIN.name);
    await importPubchemCompoundAndWait(page, pubchemDialog, ASPIRIN.name);
    await secondDoc.saveAndView();

    const searchDialog = await searchExactForAspirin(pageWorkspace);

    expect(await searchDialog.resultCount()).toBeGreaterThanOrEqual(2);
  });

  test("As a user, deleting and restoring a document preserves its chemical structure (Chemical Search still finds a deleted document's structure -- a known bug)", async ({
    pageWorkspace,
    pageDeletedItems,
    page,
  }) => {
    const docName = "Untitled document";
    const { docEditor, docId } = await insertAspirinViaKetcher(pageWorkspace, page);
    await docEditor.saveAndView();

    const searchDialog = await searchExactForAspirin(pageWorkspace);
    expect(await searchDialog.resultCount()).toBeGreaterThan(0);
    await searchDialog.close();

    await pageWorkspace.open();
    await pageWorkspace.table.selectRecord(docName);
    await pageWorkspace.selectionBar.delete();
    expect(await pageWorkspace.table.row(docName).count()).toBe(0);

    await pageDeletedItems.open();
    await pageDeletedItems.isLoaded();
    await pageDeletedItems.search(docName);
    await pageDeletedItems.restore(docName);

    const restoredDoc = await pageWorkspace.openDocument(docId);
    const field = await restoredDoc.getFieldViewContent("New List of Materials");
    await expect(field.locator('img[src*="sourceType=CHEM"]')).toBeVisible();
  });

  test("As a user, I can create a snippet from selected content that includes a chemical structure", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    await insertFluoresceinViaGallery(docEditor);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
    await field.selectAll();

    const snippetName = `e2e-chem-snippet-${Date.now()}`;
    const snippetDialog = await docEditor.openCreateSnippetDialog();
    await snippetDialog.create(snippetName);
  });

  test("As a user, uploading a ChemDraw (.cdx) file to the Gallery is converted into a real chemical structure", async ({
    page,
  }) => {
    const response = await page.request.post("/gallery/ajax/uploadFile", {
      multipart: {
        xfile: {
          name: "Fluorescein1.cdx",
          mimeType: "application/octet-stream",
          buffer: readFileSync(CHEMDRAW_CDX),
        },
      },
    });
    expect(response.ok()).toBe(true);

    const { data, error } = await response.json();
    expect(error).toBeFalsy();
    expect(data.name).toBe("Fluorescein1.cdx");
    expect(data.chemString).toBeTruthy();
  });
});
