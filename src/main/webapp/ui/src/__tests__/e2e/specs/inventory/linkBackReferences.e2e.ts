import { expect } from "@playwright/test";
import type { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const TINY_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);

async function createSampleLinkingTo(
  pageInventory: InventoryPage,
  componentToasts: ToastsComponent,
  {
    sampleName,
    fieldName,
    relationType,
    targetGlobalId,
  }: { sampleName: string; fieldName: string; relationType: string; targetGlobalId: string },
): Promise<void> {
  await pageInventory.open();
  await pageInventory.isLoaded();
  const menu = await pageInventory.openCreateMenu();
  const form = await menu.newSample();
  await form.fillName(sampleName);
  await form.expandSection("Custom Fields");
  const editor = await form.customFields().addNewLinkField(fieldName);
  await editor.setRelationType(relationType);
  await editor.setTargetGlobalId(targetGlobalId);
  await editor.apply();
  await form.save();
  await expect(componentToasts.byVariant("success", sampleName)).toBeVisible({ timeout: 30_000 });
}

test.describe(`Inventory link back-references on ELN records`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can see the linking Inventory item in a Gallery file's info panel`, async ({
    clientFiles,
    pageInventory,
    pageGallery,
    componentToasts,
  }) => {
    const fileName = `${uniqueName("e2e-linkback")}.png`;
    const sampleName = uniqueName("e2e-linkback-source");
    const file = await clientFiles.uploadFile({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });

    await createSampleLinkingTo(pageInventory, componentToasts, {
      sampleName,
      fieldName: "Related File",
      relationType: "HasMetadata",
      targetGlobalId: file.globalId,
    });

    await pageGallery.openFile(file.id);
    await pageGallery.isLoaded();
    await pageGallery.infoPanel.waitUntilSelected(fileName);
    await pageGallery.infoPanel.relatedInventoryHeading.scrollIntoViewIfNeeded();
    await expect(pageGallery.infoPanel.relatedInventoryHeading).toBeVisible();
    const row = pageGallery.infoPanel.relatedInventoryRow(sampleName);
    await expect(row).toBeVisible();
    await expect(row).toContainText("HasMetadata");
  });

  test(`As a user, I can see the linking Inventory item in an ELN document's record-info dialog`, async ({
    clientDocuments,
    pageInventory,
    pageWorkspace,
    componentToasts,
  }) => {
    const docName = uniqueName("e2e-linkback-doc");
    const sampleName = uniqueName("e2e-linkback-doc-source");
    const doc = await clientDocuments.create({ name: docName });

    await createSampleLinkingTo(pageInventory, componentToasts, {
      sampleName,
      fieldName: "Related Document",
      relationType: "IsDescribedBy",
      targetGlobalId: doc.globalId,
    });

    await pageWorkspace.open(doc.parentFolderId);
    if (await pageWorkspace.isTreeView()) {
      await pageWorkspace.toolbar.switchLayout("list");
    }
    const infoDialog = await pageWorkspace.openInfoFor(docName);
    await expect(infoDialog.relatedInventoryItemsContent).toContainText(sampleName);
    await expect(infoDialog.relatedInventoryItemsContent).toContainText("IsDescribedBy");
    await infoDialog.close();
  });
});
