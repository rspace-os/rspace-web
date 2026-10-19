import { expect, type Locator } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath, uniqueName } from "@/__tests__/e2e/testData";
import { insertReactionAndCalculate } from "./stoichiometryTestHelpers";

const MAGNESIUM_CITRATE_CDXML = fixturePath(import.meta.url, "fixtures/magnesium_citrate.cdxml");
const MAGNESIUM_CITRATE_FILE_NAME = "magnesium_citrate.cdxml";
const ASPIRIN_SMILES = "CC(=O)Oc1ccccc1C(=O)O";
// `addFromPubChem` uses the same mock/real PubChem split as pubchem.e2e.ts (see e2e-mocking.md).
// Its asserted molecular weight is never a PubChem value though -- PubChem only supplies a
// name/SMILES/formula; weight is always computed locally by the (never-mocked) chemistry
// container, so this passes in both modes without a mode-conditional assertion.

/** Reads every embedded stoichiometry table's id from a view-mode field's rendered HTML (`data-stoichiometry-table`). */
async function getStoichiometryTableIds(fieldDiv: Locator): Promise<number[]> {
  return fieldDiv.locator("[data-stoichiometry-table]").evaluateAll((elements) =>
    elements.map((element) => {
      const attr = element.getAttribute("data-stoichiometry-table") ?? "{}";
      return (JSON.parse(attr) as { id: number }).id;
    }),
  );
}

test.describe("Stoichiometry", { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, calculating stoichiometry for an inserted reaction shows the expected compounds and recalculates mass edits", async ({
    pageWorkspace,
  }) => {
    const { stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);

    expect(await stoichiometryDialog.hasCompound("Benzene")).toBe(true);
    expect(await stoichiometryDialog.hasCompound("Cyclopentadiene")).toBe(true);
    expect(await stoichiometryDialog.hasCompound("Cyclohexane")).toBe(true);

    const molesBefore = await stoichiometryDialog.getCellText("Benzene", "moles");
    await stoichiometryDialog.editCell("Benzene", "mass", "1.00");
    const molesAfter = await stoichiometryDialog.getCellText("Benzene", "moles");
    expect(molesAfter).not.toBe(molesBefore);
  });

  test("As a user, I can add reagents to a stoichiometry table from PubChem, Gallery, and manually by SMILES, and a newly-added reagent's molecular weight persists after saving and reloading", async ({
    pageWorkspace,
    page,
  }) => {
    let { docEditor, field, stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);

    await stoichiometryDialog.addFromPubChem("Aspirin");
    const aspirinMolecularWeight = await stoichiometryDialog.getCellText("Aspirin", "molecularWeight");
    expect(aspirinMolecularWeight).toBe("180.160");

    await stoichiometryDialog.addFromGallery(MAGNESIUM_CITRATE_CDXML, MAGNESIUM_CITRATE_FILE_NAME);
    expect(await stoichiometryDialog.hasCompound("magnesium_citrate")).toBe(true);

    const manualName = "e2e-manual-smiles";
    await stoichiometryDialog.addManually(manualName, ASPIRIN_SMILES);
    expect(await stoichiometryDialog.hasCompound(manualName)).toBe(true);

    await stoichiometryDialog.saveChanges();
    await stoichiometryDialog.close();
    await docEditor.editToolbar.save();

    await page.reload();
    field = await docEditor.getField("New List of Materials");
    stoichiometryDialog = await field.chemistry.openStoichiometryDialog();

    expect(await stoichiometryDialog.hasCompound("Aspirin")).toBe(true);
    expect(await stoichiometryDialog.getCellText("Aspirin", "molecularWeight")).toBe(aspirinMolecularWeight);

    const molesBefore = await stoichiometryDialog.getCellText("Benzene", "moles");
    await stoichiometryDialog.editCell("Benzene", "mass", "1.00");
    const molesAfter = await stoichiometryDialog.getCellText("Benzene", "moles");
    expect(molesAfter).not.toBe(molesBefore);
  });

  test("As a user, I can link stoichiometry reagents to Inventory subsamples", async ({
    pageWorkspace,
    clientInventory,
  }) => {
    const sample = await clientInventory.createSample({
      name: `e2e-stoichiometry-${Date.now()}`,
      newSampleSubSamplesCount: 2,
    });
    const [firstSubSample, secondSubSample] = sample.subSamples;

    const { stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);

    const firstLinkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Benzene");
    await firstLinkDialog.search(firstSubSample.name);
    await firstLinkDialog.select(firstSubSample.name);
    await firstLinkDialog.choose();
    expect(await stoichiometryDialog.hasInventoryLink("Benzene")).toBe(true);

    const secondLinkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Cyclopentadiene");
    await secondLinkDialog.search(secondSubSample.name);
    await secondLinkDialog.select(secondSubSample.name);
    await secondLinkDialog.choose();
    expect(await stoichiometryDialog.hasInventoryLink("Cyclopentadiene")).toBe(true);

    await stoichiometryDialog.removeInventoryLink("Benzene");
    expect(await stoichiometryDialog.hasInventoryLink("Benzene")).toBe(false);

    const thirdLinkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Benzene");
    await thirdLinkDialog.search(secondSubSample.name);
    expect(await thirdLinkDialog.isRowDisabled(secondSubSample.name)).toBe(true);
    await thirdLinkDialog.cancel();
  });

  test("As a user, updating inventory stock deducts the exact actual amount used, doing it again is cumulative, and exceeding stock is blocked", async ({
    pageWorkspace,
    clientInventory,
  }) => {
    const benzeneSample = await clientInventory.createSample({
      name: `e2e-stock-${Date.now()}`,
      newSampleSubSamplesCount: 1,
      quantity: { numericValue: 10, unitId: 7 },
    });
    const benzeneSubSampleId = benzeneSample.subSamples[0].id;
    const cyclopentadieneSample = await clientInventory.createSample({
      name: `e2e-stock-overlimit-${Date.now()}`,
      newSampleSubSamplesCount: 1,
      quantity: { numericValue: 5, unitId: 7 },
    });
    const cyclopentadieneSubSampleId = cyclopentadieneSample.subSamples[0].id;

    const { stoichiometryDialog } = await insertReactionAndCalculate(pageWorkspace);
    const benzeneLinkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Benzene");
    await benzeneLinkDialog.search(benzeneSample.subSamples[0].name);
    await benzeneLinkDialog.select(benzeneSample.subSamples[0].name);
    await benzeneLinkDialog.choose();
    const cyclopentadieneLinkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Cyclopentadiene");
    await cyclopentadieneLinkDialog.search(cyclopentadieneSample.subSamples[0].name);
    await cyclopentadieneLinkDialog.select(cyclopentadieneSample.subSamples[0].name);
    await cyclopentadieneLinkDialog.choose();

    await test.step("An exact deduction reduces stock by the actual amount used", async () => {
      await stoichiometryDialog.editCell("Benzene", "actualAmount", "3");
      await stoichiometryDialog.saveChanges();
      const firstUpdateDialog = await stoichiometryDialog.openUpdateInventoryStockDialog();
      await firstUpdateDialog.save();
      expect((await clientInventory.getSubSample(benzeneSubSampleId)).quantity).toEqual({
        numericValue: 7,
        unitId: 7,
      });
    });

    await test.step("A second deduction on the same molecule is cumulative, not a replacement", async () => {
      await stoichiometryDialog.editCell("Benzene", "actualAmount", "2");
      await stoichiometryDialog.saveChanges();
      const secondUpdateDialog = await stoichiometryDialog.openUpdateInventoryStockDialog();
      expect(await secondUpdateDialog.isStockAlreadyDeducted("Benzene")).toBe(true);
      await secondUpdateDialog.selectMolecule("Benzene");
      await secondUpdateDialog.save();
      expect((await clientInventory.getSubSample(benzeneSubSampleId)).quantity).toEqual({
        numericValue: 5,
        unitId: 7,
      });
    });

    await test.step("Exceeding the now-5g remaining on an already-deducted row is rejected server-side", async () => {
      await stoichiometryDialog.editCell("Benzene", "actualAmount", "999");
      await stoichiometryDialog.saveChanges();
      const thirdUpdateDialog = await stoichiometryDialog.openUpdateInventoryStockDialog();
      await thirdUpdateDialog.selectMolecule("Benzene");
      await thirdUpdateDialog.save();
      expect(await thirdUpdateDialog.errorMessage()).toContain("Insufficient stock");
      expect((await clientInventory.getSubSample(benzeneSubSampleId)).quantity).toEqual({
        numericValue: 5,
        unitId: 7,
      });
      await thirdUpdateDialog.cancel();
    });

    await test.step("A first-time deduction exceeding available stock is blocked client-side before it can be saved", async () => {
      await stoichiometryDialog.editCell("Cyclopentadiene", "actualAmount", "999");
      await stoichiometryDialog.saveChanges();
      const cyclopentadieneUpdateDialog = await stoichiometryDialog.openUpdateInventoryStockDialog();
      expect(await cyclopentadieneUpdateDialog.remainingText("Cyclopentadiene")).toContain("-994.0 g");
      await expect(cyclopentadieneUpdateDialog.checkbox("Cyclopentadiene")).toBeDisabled();
      await expect(cyclopentadieneUpdateDialog.saveButton).toBeDisabled();
      expect((await clientInventory.getSubSample(cyclopentadieneSubSampleId)).quantity).toEqual({
        numericValue: 5,
        unitId: 7,
      });
    });
  });

  test("As a user, I can create a standalone reaction table with no pre-existing chemical structure", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const field = await docEditor.getField("New List of Materials");
    const stoichiometryDialog = await field.chemistry.openStandaloneStoichiometryDialog();

    const manualName = "e2e-standalone-reagent";
    await stoichiometryDialog.addManually(manualName, ASPIRIN_SMILES);
    expect(await stoichiometryDialog.hasCompound(manualName)).toBe(true);
  });

  test("As a user, duplicating a document gives its stoichiometry tables independent copies, for both reaction-linked and standalone tables", async ({
    pageWorkspace,
    pageDocument,
    clientStoichiometry,
  }) => {
    const docName = uniqueName("e2e-stoich-copy");
    const { docEditor, field, stoichiometryDialog: reactionDialog } = await insertReactionAndCalculate(pageWorkspace);
    await reactionDialog.close();
    await docEditor.header.rename(docName);

    const standaloneDialog = await field.chemistry.openStandaloneStoichiometryDialog();
    await standaloneDialog.addManually("e2e-standalone-for-copy", ASPIRIN_SMILES);
    await standaloneDialog.saveChanges();
    await standaloneDialog.close();
    await field.chemistry.waitForStandaloneStoichiometryTable();

    const docViewPage = await docEditor.saveAndView();
    const originalFieldDiv = await docViewPage.getFieldViewContent("New List of Materials");
    await expect(originalFieldDiv.locator("[data-stoichiometry-table]")).toHaveCount(2);
    const originalIds = await getStoichiometryTableIds(originalFieldDiv);
    expect(originalIds).toHaveLength(2);

    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.selectRecord(docName);
    await pageWorkspace.selectionBar.clickAction("Duplicate");
    await expect(pageWorkspace.table.row(`${docName}_Copy`)).toBeVisible();
    await pageWorkspace.table.openRecord(`${docName}_Copy`);

    const copyFieldDiv = await pageDocument.getFieldViewContent("New List of Materials");
    await expect(copyFieldDiv.locator("[data-stoichiometry-table]")).toHaveCount(2);
    const copyIds = await getStoichiometryTableIds(copyFieldDiv);
    expect(copyIds).toHaveLength(2);
    expect(copyIds).not.toContain(originalIds[0]);
    expect(copyIds).not.toContain(originalIds[1]);

    const copiedTables = await Promise.all(copyIds.map((id) => clientStoichiometry.getById(id)));
    const copiedReactionTable = copiedTables.find((table) =>
      table.molecules.some((molecule) => molecule.name === "Benzene"),
    );
    const copiedStandaloneTable = copiedTables.find((table) =>
      table.molecules.some((molecule) => molecule.name === "e2e-standalone-for-copy"),
    );
    expect(copiedReactionTable?.molecules.map((m) => m.name)).toEqual(
      expect.arrayContaining(["Benzene", "Cyclopentadiene", "Cyclohexane"]),
    );
    expect(copiedStandaloneTable?.molecules.map((m) => m.name)).toContain("e2e-standalone-for-copy");
  });
});
