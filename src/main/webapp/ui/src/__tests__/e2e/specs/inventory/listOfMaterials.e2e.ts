import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`List of Materials with Instruments`, { tag: [tags.INVENTORY] }, () => {
  test(`As a user, I can add an Instrument to a document's List of Materials`, async ({
    pageWorkspace,
    clientInventory,
  }) => {
    const instrumentName = uniqueName("e2e-lom-instrument");
    const instrument = await clientInventory.createInstrument({ name: instrumentName });

    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();

    const materials = await editor.openNewListOfMaterialsDialog();
    await materials.addItems();
    await materials.filterPickerByType("Instruments");
    await materials.searchPicker(instrumentName);
    await materials.selectPickerResult(instrumentName);
    await materials.choosePickerSelection();

    await expect(materials.materialRow(instrumentName)).toBeVisible();
    await materials.save();

    const lists = await clientInventory.getListOfMaterialsForInventoryItem(instrument.globalId);
    expect(lists.length).toBeGreaterThan(0);
  });
});
