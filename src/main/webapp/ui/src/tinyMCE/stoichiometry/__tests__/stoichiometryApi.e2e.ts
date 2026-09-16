import { expect, type Locator } from "@playwright/test";
import { toMoleculeUpdate } from "@/__tests__/e2e/api/models/stoichiometry";
import type { TinyMceEditor } from "@/__tests__/e2e/components/document/TinyMceEditor";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath } from "@/__tests__/e2e/testData";
import type { StoichiometryDialogComponent } from "./pageObjects/StoichiometryDialogComponent";

const REACTION_CDXML = fixturePath(import.meta.url, "fixtures/basic_reaction.cdxml");
const REACTION_FILE_NAME = "basic_reaction.cdxml";

/** Creates a Basic Document, inserts the reaction fixture from the Gallery, and calculates its stoichiometry table. */
async function insertReactionAndCalculate(pageWorkspace: WorkspacePage): Promise<{
  docEditor: DocumentEditorPage;
  field: TinyMceEditor;
  stoichiometryDialog: StoichiometryDialogComponent;
}> {
  await pageWorkspace.open();
  const docEditor = await pageWorkspace.createBasicDocument();
  const picker = await docEditor.openGalleryPicker();
  await picker.goToSection("Chemistry");
  await picker.uploadFile(REACTION_CDXML, REACTION_FILE_NAME);
  await picker.selectItem(REACTION_FILE_NAME);
  await picker.add();

  const field = await docEditor.getField("New List of Materials");
  const stoichiometryDialog = await field.chemistry.openStoichiometryDialog();
  await stoichiometryDialog.calculate();
  return { docEditor, field, stoichiometryDialog };
}

/** Reads the `data-stoichiometry-table` JSON attribute (`{id, revision}`) off a view-mode field's rendered HTML. */
async function getStoichiometryTableAttr(fieldDiv: Locator): Promise<{ id: number; revision: number | null }> {
  const attr = await fieldDiv.locator("[data-stoichiometry-table]").first().getAttribute("data-stoichiometry-table");
  return JSON.parse(attr ?? "{}");
}

function findMoleculeOrThrow<T extends { name: string }>(molecules: T[], name: string): T {
  const molecule = molecules.find((m) => m.name === name);
  if (!molecule) throw new Error(`"${name}" molecule not found in stoichiometry response`);
  return molecule;
}

test.describe("Stoichiometry API (RSDEV-1005 updateFieldHtml)", { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });
  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, PUT/deductStock/DELETE via the API only sync the document's embedded HTML when updateFieldHtml is true", async ({
    pageWorkspace,
    clientStoichiometry,
    clientInventory,
  }) => {
    const sample = await clientInventory.createSample({
      name: `e2e-stoich-api-${Date.now()}`,
      newSampleSubSamplesCount: 1,
      quantity: { numericValue: 10, unitId: 7 },
    });
    const subSampleId = sample.subSamples[0].id;

    const { docEditor, stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);
    const linkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Benzene");
    await linkDialog.search(sample.subSamples[0].name);
    await linkDialog.select(sample.subSamples[0].name);
    await linkDialog.choose();
    await stoichiometryDialog.editCell("Benzene", "actualAmount", "3");
    await stoichiometryDialog.saveChanges();
    await stoichiometryDialog.close();

    const docViewPage = await docEditor.saveAndView();
    let fieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
    const initial = await getStoichiometryTableAttr(fieldDiv);

    await test.step("PUT with updateFieldHtml=true syncs the embedded HTML", async () => {
      const current = await clientStoichiometry.getById(initial.id);
      const molecules = current.molecules.map(toMoleculeUpdate);
      findMoleculeOrThrow(molecules, "Cyclopentadiene").mass = 5;
      const updated = await clientStoichiometry.update(initial.id, { id: initial.id, molecules }, true);
      expect(updated.revision).not.toBe(initial.revision);

      await docViewPage.reload();
      fieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
      const afterTrue = await getStoichiometryTableAttr(fieldDiv);
      expect(afterTrue.revision).toBe(updated.revision);
    });

    await test.step("PUT with updateFieldHtml=false changes the entity but leaves the embedded HTML stale", async () => {
      const beforeFalse = await getStoichiometryTableAttr(fieldDiv);
      const current = await clientStoichiometry.getById(initial.id);
      const molecules = current.molecules.map(toMoleculeUpdate);
      findMoleculeOrThrow(molecules, "Cyclohexane").mass = 6;
      const updated = await clientStoichiometry.update(initial.id, { id: initial.id, molecules }, false);
      expect(updated.revision).not.toBe(beforeFalse.revision);

      await docViewPage.reload();
      fieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
      const afterFalse = await getStoichiometryTableAttr(fieldDiv);
      expect(afterFalse.revision).toBe(beforeFalse.revision);
    });

    await test.step("deductStock with updateFieldHtml=true deducts inventory and syncs the embedded HTML", async () => {
      const beforeDeduct = await getStoichiometryTableAttr(fieldDiv);
      const current = await clientStoichiometry.getById(initial.id);
      const benzene = findMoleculeOrThrow(current.molecules, "Benzene");
      if (!benzene.inventoryLink) throw new Error("Benzene inventory link not found");
      const result = await clientStoichiometry.deductStock({
        stoichiometryId: initial.id,
        linkIds: [benzene.inventoryLink.id],
        updateFieldHtml: true,
      });
      expect(result.results[0]).toMatchObject({ linkId: benzene.inventoryLink.id, success: true });
      expect((await clientInventory.getSubSample(subSampleId)).quantity).toEqual({ numericValue: 7, unitId: 7 });

      await docViewPage.reload();
      fieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
      const afterDeduct = await getStoichiometryTableAttr(fieldDiv);
      expect(afterDeduct.revision).toBe(result.revisionNumber);
      expect(afterDeduct.revision).not.toBe(beforeDeduct.revision);
    });

    await test.step("DELETE with updateFieldHtml=true strips the reference but keeps the chem image", async () => {
      await clientStoichiometry.remove(initial.id, true);

      await docViewPage.reload();
      fieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
      const chemImage = fieldDiv.locator("img.chem").first();
      await chemImage.waitFor({ state: "visible" });
      expect(await chemImage.getAttribute("data-stoichiometry-table")).toBeNull();
    });
  });

  test("As a user, updating stoichiometry via the API is blocked with 409 while the document is open for editing", async ({
    pageWorkspace,
    clientStoichiometry,
  }) => {
    const { docEditor, field, stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);
    await stoichiometryDialog.close();

    const attr = await field.chemistry.chemElement.getAttribute("data-stoichiometry-table");
    const { id } = JSON.parse(attr ?? "{}") as { id: number };
    const current = await clientStoichiometry.getById(id);
    const molecules = current.molecules.map(toMoleculeUpdate);

    const conflictResponse = await clientStoichiometry.updateRaw(id, { id, molecules }, true);
    expect(conflictResponse.status()).toBe(409);

    await docEditor.saveAndView();
    const updated = await clientStoichiometry.update(id, { id, molecules }, true);
    expect(updated.id).toBe(id);
  });
});
