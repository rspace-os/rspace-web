import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Document List of Materials", { tag: tags.INVENTORY }, () => {
  test("As a user, a document preserves its linked inventory subsample after save and reopen", async ({
    clientInventory,
    pageWorkspace,
    pageDocument,
  }) => {
    const sample = await clientInventory.createSample({
      name: uniqueName("e2e-document-material"),
      newSampleSubSamplesCount: 1,
    });
    const subsample = sample.subSamples[0];
    const docName = uniqueName("e2e-materials-document");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(docName);
    const materials = await editor.openNewListOfMaterialsDialog();
    await materials.addItems();
    await materials.searchPicker(subsample.name);
    await materials.selectPickerResult(subsample.name);
    await materials.choosePickerSelection();
    await expect(materials.materialRow(subsample.name)).toBeVisible();
    await materials.save();
    await materials.close();
    await editor.editToolbar.saveAndClose();

    const lists = await clientInventory.getListOfMaterialsForInventoryItem(subsample.globalId);
    expect(lists).toHaveLength(1);
    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.isLoaded();
    const reopened = await pageDocument.openListOfMaterials(lists[0].name);
    await expect(reopened.materialRow(subsample.name)).toBeVisible();
    await expect(reopened.materialRow(subsample.name)).toContainText(subsample.globalId);
    await reopened.close();
  });
});
