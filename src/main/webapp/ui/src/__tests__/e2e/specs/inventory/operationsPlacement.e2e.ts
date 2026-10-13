import { expect } from "@playwright/test";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { ProcessWizardComponent } from "@/__tests__/e2e/components/inventory/ProcessWizardComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

// Remembered process values are per account and change where the wizard opens, so every test here
// runs as a fresh dynamic user.

const ML = 3;

async function origin(client: InventoryClient, subSampleCount = 1) {
  return client.createSample({
    name: uniqueName("e2e-place"),
    newSampleSubSamplesCount: subSampleCount,
    quantity: { numericValue: 10, unitId: ML },
  });
}

async function listContainer(client: InventoryClient, prefix = "e2e-shelf") {
  return client.createContainer({ name: uniqueName(prefix), cType: "LIST" });
}

async function gridContainer(client: InventoryClient, rows: number, columns: number) {
  return client.createContainer({
    name: uniqueName("e2e-box"),
    cType: "GRID",
    gridLayout: { columnsNumber: columns, rowsNumber: rows },
  });
}

async function openWizard(pageInventory: InventoryPage, sampleName: string, subSampleNames: string[]) {
  await pageInventory.openSearch("SUBSAMPLE");
  await pageInventory.searchPanel.search(sampleName);
  for (const name of subSampleNames) {
    await pageInventory.searchPanel.selectItem(name);
  }
  return pageInventory.searchPanel.batchActions.openProcessWizard();
}

async function aliquotToLocation(
  pageInventory: InventoryPage,
  sample: { name: string; subSamples: Array<{ name: string }> },
  count?: number,
): Promise<ProcessWizardComponent> {
  const wizard = await openWizard(pageInventory, sample.name, [sample.subSamples[0].name]);
  await wizard.chooseOperation("Aliquot");
  await wizard.goToLocation({ count });
  return wizard;
}

test.describe("Inventory operation placement", { tag: tags.INVENTORY }, () => {
  test.describe("on a phone too", { tag: tags.MOBILE }, () => {
    test("As a user, I can aliquot into a list container", async ({
      flowOperationsEnabled: _,
      pageInventory,
      clientInventory,
    }) => {
      const sample = await origin(clientInventory);
      const shelf = await listContainer(clientInventory);
      const wizard = await aliquotToLocation(pageInventory, sample);

      await wizard.location.pick(shelf.name);

      await expect(wizard.location.placingIn).toHaveText(`Placing in ${shelf.name} (${shelf.globalId})`);
      await expect(wizard.location.changeContainerButton).toBeVisible();
      await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${shelf.name}.`);
      await wizard.next();
      expect(await wizard.confirmedLocation()).toBe(shelf.name);
      await wizard.perform();

      await expect
        .poll(async () => (await clientInventory.searchByParent(shelf.globalId, "SUBSAMPLE")).records.length)
        .toBe(1);
      const originAfter = await clientInventory.getSubSample(sample.subSamples[0].id);
      expect(originAfter.parentContainers?.[0].cType).toBe("WORKBENCH");
    });
  });

  test("As a user, the new subsamples stay on my workbench by default", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const wizard = await aliquotToLocation(pageInventory, sample);

    await expect(wizard.location.workbenchRadio).toBeChecked();
    await wizard.next();
    expect(await wizard.confirmedLocation()).toBe("Your workbench");
    await wizard.perform();

    const bench = (await clientInventory.getSubSample(sample.subSamples[0].id)).parentContainers?.[0];
    const created = await clientInventory.searchByName(`${sample.name} aliquot`, "SUBSAMPLE");
    expect(created.records.length).toBeGreaterThan(0);
    for (const record of created.records) {
      expect((await clientInventory.getSubSample(record.id)).parentContainers?.[0].id).toBe(bench?.id);
    }
  });

  test("As a user, I can change the container I picked", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const box = await gridContainer(clientInventory, 2, 2);
    const shelf = await listContainer(clientInventory);
    const wizard = await aliquotToLocation(pageInventory, sample, 2);
    await wizard.location.pick(box.name);
    await expect(wizard.location.grid.root).toBeVisible();

    await wizard.location.changeContainer();

    await expect(wizard.location.placingIn).toBeHidden();
    await expect(wizard.location.status).toBeHidden();
    await expect(wizard.location.grid.root).toBeHidden();
    await expect(wizard.nextButton).toBeDisabled();
    await wizard.location.pick(shelf.name);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${shelf.name}.`);
    await expect(wizard.nextButton).toBeEnabled();
  });

  test("As a user, the container my subsample is in is offered first", async ({
    flowOperationsEnabled: _,
    pageInventory,
    clientInventory,
  }) => {
    const sample = await origin(clientInventory);
    const rack = await listContainer(clientInventory, "e2e-rack");
    await clientInventory.moveSubSample(sample.subSamples[0].id, { parentContainers: [{ id: rack.id }] });

    const wizard = await aliquotToLocation(pageInventory, sample);

    await expect(wizard.location.containerRadio).toBeChecked();
    await expect(wizard.location.placingIn).toContainText(rack.name);
    await expect(wizard.location.status).toHaveText(`The new subsamples will be placed in ${rack.name}.`);
    await expect(wizard.nextButton).toBeEnabled();
  });
});
