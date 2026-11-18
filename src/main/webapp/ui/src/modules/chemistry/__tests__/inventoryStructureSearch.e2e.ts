import { readFileSync } from "node:fs";
import { expect } from "@playwright/test";
import type { FilesClient } from "@/__tests__/e2e/api/clients/FilesClient";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath, uniqueName } from "@/__tests__/e2e/testData";

// Fluorescein contains benzene rings, so benzene matches it as a substructure but not exactly.
const FLUORESCEIN = fixturePath(import.meta.url, "fixtures/Fluorescein1.cdx");
const BENZENE = "c1ccccc1";

/**
 * A sample whose single subsample is stored in its own container, with Fluorescein from the
 * Gallery attached either to the sample or to the subsample.
 */
async function sampleWithFluorescein(
  clientInventory: InventoryClient,
  clientFiles: FilesClient,
  attachTo: "sample" | "subsample",
) {
  const sample = await clientInventory.createSample({
    name: uniqueName("structure-sample"),
    newSampleSubSamplesCount: 1,
  });
  const subSample = sample.subSamples[0];
  const container = await clientInventory.createContainer({ name: uniqueName("structure-box"), cType: "LIST" });
  await clientInventory.moveSubSample(subSample.id, { parentContainers: [{ id: container.id }] });
  const galleryFile = await clientFiles.uploadFile({
    name: "Fluorescein1.cdx",
    mimeType: "chemical/x-cdx",
    buffer: readFileSync(FLUORESCEIN),
  });
  await clientInventory.attachGalleryFile(
    attachTo === "sample" ? sample.globalId : subSample.globalId,
    galleryFile.globalId,
  );
  return { sample, subSample, container };
}

test.describe("Inventory structure search", { tag: [tags.APPS, tags.INVENTORY] }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, I can paste a SMILES structure into Inventory search and find the sample carrying it", async ({
    clientInventory,
    clientFiles,
    pageInventory,
  }) => {
    const { sample } = await sampleWithFluorescein(clientInventory, clientFiles, "sample");
    await pageInventory.open();

    const dialog = await pageInventory.searchPanel.openChemicalSearch();
    await dialog.pasteSmiles(BENZENE);
    await dialog.search();
    await pageInventory.searchPanel.waitForResultsLoaded();

    await expect(pageInventory.searchPanel.row(sample.name)).toBeVisible();
    await expect(pageInventory.searchPanel.chemicalFilterChip()).toBeVisible();
  });

  test("As a user, I can draw benzene, find the matching sample and open it to see where it is stored", async ({
    clientInventory,
    clientFiles,
    pageInventory,
  }) => {
    const { sample, container } = await sampleWithFluorescein(clientInventory, clientFiles, "sample");
    await pageInventory.open();

    const dialog = await pageInventory.searchPanel.openChemicalSearch();
    await dialog.drawSmiles(BENZENE);
    await dialog.search();
    await pageInventory.searchPanel.waitForResultsLoaded();

    // the sample carrying the structure is the hit; its subsample is not listed separately
    expect(await pageInventory.searchPanel.rowCount()).toBe(1);
    await pageInventory.searchPanel.open(sample.name);
    await expect(pageInventory.detailsPanel.root).toContainText(container.name);
  });

  test("As a user, a subsample carrying the structure is listed with its location", async ({
    clientInventory,
    clientFiles,
    pageInventory,
  }) => {
    const { subSample, container } = await sampleWithFluorescein(clientInventory, clientFiles, "subsample");
    await pageInventory.open();

    const dialog = await pageInventory.searchPanel.openChemicalSearch();
    await dialog.drawSmiles(BENZENE);
    await dialog.search();
    await pageInventory.searchPanel.waitForResultsLoaded();

    // the Location column is the third adjustable column, which needs the wider, panel-less layout
    await pageInventory.searchPanel.hideRightPanel();
    await expect(pageInventory.searchPanel.row(subSample.name)).toContainText(container.name);
  });

  test("As a user, benzene matches a sample as a substructure but not as an exact structure", async ({
    clientInventory,
    clientFiles,
    pageInventory,
  }) => {
    const { sample } = await sampleWithFluorescein(clientInventory, clientFiles, "sample");
    await pageInventory.open();

    const exactDialog = await pageInventory.searchPanel.openChemicalSearch();
    await exactDialog.setSearchType("EXACT");
    await exactDialog.drawSmiles(BENZENE);
    await exactDialog.search();
    await pageInventory.searchPanel.waitForResultsLoaded();
    await expect(pageInventory.searchPanel.row(sample.name)).toHaveCount(0);

    const substructureDialog = await pageInventory.searchPanel.editChemicalFilter();
    await substructureDialog.setSearchType("SUBSTRUCTURE");
    await substructureDialog.search();
    await pageInventory.searchPanel.waitForResultsLoaded();
    await expect(pageInventory.searchPanel.row(sample.name)).toBeVisible();
  });
});
