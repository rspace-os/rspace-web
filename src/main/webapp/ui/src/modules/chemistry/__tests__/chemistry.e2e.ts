import { readFileSync } from "node:fs";
import type { Locator, Page, Route } from "@playwright/test";
import { expect } from "@playwright/test";
import { CHEM_IMAGE_SELECTOR } from "@/__tests__/e2e/components/document/ChemistryFieldContent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath, uniqueName } from "@/__tests__/e2e/testData";
import type { ChemistrySearchDialogComponent } from "@/modules/chemistry/__tests__/pageObjects/ChemistrySearchDialogComponent";
import type { PubchemDialogComponent } from "@/modules/pubchem/__tests__/pageObjects/PubchemDialogComponent";

const ASPIRIN_SMILES = "CC(=O)Oc1ccccc1C(=O)O";
// The two PubChem-touching tests below use the same mock/real split as pubchem.e2e.ts
// chemistry itself is never mocked. "Aspirin" resolves identically either
// way, so neither test needs a mode-conditional assertion.
const ASPIRIN = {
  name: "Aspirin",
  formula: "C9H8O4",
};

const CHEMDRAW_CDX = fixturePath(import.meta.url, "fixtures/Fluorescein1.cdx");
const CREATE_CHEM_ELEMENT_URL = "**/chemical/ajax/createChemElement/";
const CHEM_ELEMENT_TEMPLATE_URL = "**/fieldTemplates/ajax/chemElementLink";
const MRV_STRUCTURE_A = fixturePath(import.meta.url, "fixtures/marvinjs_structure_a.mrv");
const MRV_STRUCTURE_B = fixturePath(import.meta.url, "fixtures/marvinjs_structure_b.mrv");
const MRV_STRUCTURE_AB = fixturePath(import.meta.url, "fixtures/marvinjs_structure_ab.mrv");
// A true chemical substructure of MRV_STRUCTURE_A: the same ring system with two of its three substituent branches removed.
const MRV_SUBSTRUCTURE_A = fixturePath(import.meta.url, "fixtures/marvinjs_substructure_a.mrv");

/** Holds matching requests until the test releases each one, so a pending state can be asserted. */
async function holdRequests(page: Page, url: string): Promise<{ next: () => Promise<Route> }> {
  const held: Route[] = [];
  const waiting: ((route: Route) => void)[] = [];
  await page.route(url, (route) => {
    const waiter = waiting.shift();
    if (waiter) {
      waiter(route);
    } else {
      held.push(route);
    }
  });
  return {
    next: () => {
      const route = held.shift();
      return route ? Promise.resolve(route) : new Promise((resolve) => waiting.push(resolve));
    },
  };
}

function chemdrawFile(name: string): { name: string; mimeType: string; buffer: Buffer } {
  return { name, mimeType: "application/octet-stream", buffer: readFileSync(CHEMDRAW_CDX) };
}

// The legacy jQuery blockUI overlay has no role or accessible name.
function insertingBlocker(page: Page): Locator {
  return page.locator(".blockUI.blockMsg").filter({ hasText: "Inserting chem files..." });
}

function pageBlocker(page: Page): Locator {
  return page.locator(".blockUI.blockOverlay");
}

type TinyMceEditorHandle = { id: string; getContent: () => string };
type TinyMceGlobal = {
  init: (settings: Record<string, unknown>) => Promise<TinyMceEditorHandle[]>;
  get: (id: string) => TinyMceEditorHandle;
  setActive: (editor: TinyMceEditorHandle) => void;
};

const OTHER_EDITOR_ID = "rtf_other";

/** Makes a second TinyMCE editor the active one, as if the user had moved to another field. */
async function activateAnotherEditor(page: Page): Promise<void> {
  await page.evaluate(async (id) => {
    const { tinymce } = window as unknown as { tinymce: TinyMceGlobal };
    const host = document.createElement("textarea");
    host.id = id;
    document.body.append(host);
    const [editor] = await tinymce.init({ target: host, menubar: false, toolbar: false, plugins: [] });
    tinymce.setActive(editor);
  }, OTHER_EDITOR_ID);
}

function otherEditorContent(page: Page): Promise<string> {
  return page.evaluate(
    (id) => (window as unknown as { tinymce: TinyMceGlobal }).tinymce.get(id).getContent(),
    OTHER_EDITOR_ID,
  );
}

/** Creates a Basic Document and inserts aspirin into its "New List of Materials" field via Ketcher. */
async function insertAspirinViaKetcher(
  pageWorkspace: WorkspacePage,
  page: Page,
): Promise<{ docEditor: DocumentEditorPage; docId: number }> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const docIdSegment = new URL(page.url()).pathname.split("/").pop();
  if (!docIdSegment) {
    throw new Error(`Could not parse a document id segment from URL: ${page.url()}`);
  }
  const docId = Number(docIdSegment);
  if (Number.isNaN(docId) || docId === 0) {
    throw new Error(`Parsed document id is not a valid, non-zero number: "${docIdSegment}" (from ${page.url()})`);
  }
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

/** Creates a Basic Document and inserts a structure file uploaded directly into Ketcher's own canvas. */
async function insertStructureFileViaKetcher(
  pageWorkspace: WorkspacePage,
  filePath: string,
): Promise<DocumentEditorPage> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const ketcherDialog = await docEditor.openKetcherDialog();
  await ketcherDialog.uploadStructureFile(filePath);
  await ketcherDialog.insert();
  return docEditor;
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

test.describe("Chemistry service", { tag: tags.APPS }, () => {
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

  test("As a user, dropping a chemistry file into a document keeps the editor blocked until its structure is inserted", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");
    const conversions = await holdRequests(page, CREATE_CHEM_ELEMENT_URL);
    const templates = await holdRequests(page, CHEM_ELEMENT_TEMPLATE_URL);

    await field.dropFiles([chemdrawFile("Fluorescein1.cdx")]);
    const conversion = await conversions.next();
    await expect(insertingBlocker(page)).toBeVisible();

    await conversion.continue();
    const template = await templates.next();
    await expect(insertingBlocker(page)).toBeVisible();
    await expect(field.chemistry.chemElement).toHaveCount(0);

    await template.continue();
    await expect(field.chemistry.chemElement).toBeVisible();
    await expect(pageBlocker(page)).toBeHidden();
  });

  test("As a user, a dropped chemistry file is not inserted into another field that became active while it was inserting", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");
    const templates = await holdRequests(page, CHEM_ELEMENT_TEMPLATE_URL);

    await field.dropFiles([chemdrawFile("Fluorescein1.cdx")]);
    const template = await templates.next();
    await activateAnotherEditor(page);

    await template.continue();
    await expect(page.getByText("There was a problem creating chemical: Fluorescein1.cdx")).toBeVisible();
    await expect(pageBlocker(page)).toBeHidden();
    await expect(field.chemistry.chemElement).toHaveCount(0);
    expect(await otherEditorContent(page)).toBe("");
  });

  test("As a user, when some dropped chemistry files fail, the editor stays blocked until every file settles", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");
    const conversions = await holdRequests(page, CREATE_CHEM_ELEMENT_URL);
    const templates = await holdRequests(page, CHEM_ELEMENT_TEMPLATE_URL);

    await field.dropFiles([chemdrawFile("Fluorescein1.cdx"), chemdrawFile("Fluorescein2.cdx")]);
    const failedConversion = await conversions.next();
    const conversion = await conversions.next();
    await expect(insertingBlocker(page)).toBeVisible();

    await failedConversion.fulfill({ json: { error: { errorMessages: ["Conversion failed"] } } });
    await expect(page.getByText("There was a problem creating chemical: Fluorescein1.cdx")).toBeVisible();
    await expect(insertingBlocker(page)).toBeVisible();

    await conversion.continue();
    await (await templates.next()).abort();
    await expect(page.getByText("There was a problem creating chemical: Fluorescein2.cdx")).toBeVisible();
    await expect(pageBlocker(page)).toBeHidden();
    await expect(field.chemistry.chemElement).toHaveCount(0);

    await field.typeAtEnd(" still editable");
    await expect(field.body).toContainText("still editable");
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
    await expect.poll(() => viewerDialog.isAtomToolEnabled("C")).toBe(false);
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
    const docEditor = await insertStructureFileViaKetcher(pageWorkspace, MRV_STRUCTURE_A);

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

  test("As a user, a substructure search using an uploaded structure file finds a document containing that structure", async ({
    pageWorkspace,
  }) => {
    const docEditor = await insertStructureFileViaKetcher(pageWorkspace, MRV_STRUCTURE_A);
    await docEditor.saveAndView();

    await pageWorkspace.open();
    const searchDialog = await pageWorkspace.openChemicalSearch();
    await searchDialog.setSearchType("SUBSTRUCTURE");
    await searchDialog.draw.uploadStructureFile(MRV_SUBSTRUCTURE_A);
    await searchDialog.search();

    expect(await searchDialog.resultCount()).toBeGreaterThan(0);
  });

  test("As a user, an exact search for a structure that matches no document shows no results", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const searchDialog = await pageWorkspace.openChemicalSearch();
    await searchDialog.setSearchType("EXACT");
    await searchDialog.draw.uploadStructureFile(MRV_STRUCTURE_B);
    await searchDialog.search();

    expect(await searchDialog.resultCount()).toBe(0);
    await expect(searchDialog.noResultsText).toBeVisible();
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
    test.fixme(
      true,
      "Chemical Search returns 3 results instead of 2 for a structure inserted via Ketcher + PubChem:RT-1152",
    );

    const { docEditor: firstDoc } = await insertAspirinViaKetcher(pageWorkspace, page);
    await firstDoc.saveAndView();

    await pageWorkspace.open();
    const secondDoc = await pageWorkspace.createBasicDocument();
    const pubchemDialog = await secondDoc.openPubchemDialog();
    await pubchemDialog.search(ASPIRIN.name);
    await importPubchemCompoundAndWait(page, pubchemDialog, ASPIRIN.name);
    await secondDoc.saveAndView();

    const searchDialog = await searchExactForAspirin(pageWorkspace);

    expect(await searchDialog.resultCount()).toBe(2);
  });

  test("As a user, inserting the same structure twice into a document is found twice by Chemical Search, and copying it into a snippet doesn't add a third match", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");

    let ketcherDialog = await docEditor.openKetcherDialog();
    await ketcherDialog.uploadStructureFile(MRV_STRUCTURE_A);
    await ketcherDialog.insert();

    ketcherDialog = await docEditor.openKetcherDialog();
    await ketcherDialog.uploadStructureFile(MRV_STRUCTURE_A);
    await ketcherDialog.insert();

    await expect(field.chemistry.chemElement).toHaveCount(2);

    // Snippet-ing content with both copies must not add more searchable copies.
    await field.selectAll();
    const snippetDialog = await docEditor.openCreateSnippetDialog();
    await snippetDialog.create(uniqueName("e2e-structure-a-snippet"));

    await docEditor.saveAndView();

    await pageWorkspace.open();
    const searchDialog = await pageWorkspace.openChemicalSearch();
    await searchDialog.setSearchType("EXACT");
    await searchDialog.draw.uploadStructureFile(MRV_STRUCTURE_A);
    await searchDialog.search();

    expect(await searchDialog.resultCount()).toBe(2);
  });

  test("As a user, deleting a document removes its chemical structure from Chemical Search, and restoring it brings the structure back", async ({
    pageWorkspace,
    pageDeletedItems,
    page,
    browserName,
  }) => {
    if (browserName === "webkit") {
      test.setTimeout(90_000);
    }

    const docName = uniqueName("e2e-chem-delete-restore");
    const { docEditor, docId } = await insertAspirinViaKetcher(pageWorkspace, page);
    await docEditor.header.rename(docName);
    await docEditor.saveAndView();

    const searchDialog = await searchExactForAspirin(pageWorkspace);
    expect(await searchDialog.resultCount()).toBeGreaterThan(0);
    await searchDialog.close();

    await pageWorkspace.open();
    await pageWorkspace.table.selectRecord(docName);
    await pageWorkspace.selectionBar.delete();
    expect(await pageWorkspace.table.row(docName).count()).toBe(0);

    const deletedSearchDialog = await searchExactForAspirin(pageWorkspace);
    expect(await deletedSearchDialog.resultCount()).toBe(0);
    await expect(deletedSearchDialog.noResultsText).toBeVisible();
    await deletedSearchDialog.close();

    await pageDeletedItems.open();
    await pageDeletedItems.isLoaded();
    await pageDeletedItems.search(docName);
    await pageDeletedItems.restore(docName);

    const restoredSearchDialog = await searchExactForAspirin(pageWorkspace);
    expect(await restoredSearchDialog.resultCount()).toBeGreaterThan(0);
    await restoredSearchDialog.close();

    const restoredDoc = await pageWorkspace.openDocument(docId);
    const field = await restoredDoc.getFieldViewContent("New List of Materials");
    await expect(field.locator(CHEM_IMAGE_SELECTOR)).toBeVisible();
  });

  test("As a user, I can create a snippet from selected content that includes a chemical structure", async ({
    pageWorkspace,
    pageGallery,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    await insertFluoresceinViaGallery(docEditor);

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemistry.chemElement).toBeVisible();
    await field.selectAll();

    const snippetName = uniqueName("e2e-chem-snippet");
    const snippetDialog = await docEditor.openCreateSnippetDialog();
    await snippetDialog.create(snippetName);

    await pageGallery.openInSection("Snippets");
    await expect(pageGallery.fileCell(snippetName)).toBeVisible();
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
