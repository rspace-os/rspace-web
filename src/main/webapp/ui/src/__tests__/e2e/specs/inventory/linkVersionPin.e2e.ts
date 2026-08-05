import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Inventory Link field version pinning`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can apply or discard a staged Link-field version pin`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const fieldName = "Related Item";
    const sampleName = uniqueName("e2e-link-sample");

    const target = await clientInventory.createSample({ name: uniqueName("e2e-link-target") });
    const targetGlobalId = target.globalId;
    await clientInventory.renameSample(target.id, { name: uniqueName("e2e-link-target-renamed") });
    const versions = await clientInventory.getSampleVersions(target.id);
    expect(versions.length).toBeGreaterThan(1);
    const olderVersion = versions[versions.length - 1];

    await pageInventory.open();
    await pageInventory.isLoaded();
    const createMenu = await pageInventory.openCreateMenu();
    const newSample = await createMenu.newSample();
    await newSample.fillName(sampleName);
    await newSample.expandSection("Custom Fields");

    const editor1 = await newSample.customFields().addNewLinkField(fieldName);
    await editor1.setRelationType("References");
    await editor1.setTargetGlobalId(targetGlobalId);
    await editor1.apply();
    await newSample.save();

    const detailsPanel1 = pageInventory.detailsPanel;
    await detailsPanel1.enterEditMode();
    await detailsPanel1.expandSection("Custom Fields");
    const editor2 = await detailsPanel1.customFields().editLinkField(fieldName);
    await expect.poll(() => editor2.getVersionLabel()).toBe("Latest");

    const dialog1 = await editor2.openVersionLockDialog(targetGlobalId);
    await dialog1.selectVersion(olderVersion);
    await dialog1.lockToSelectedVersion();
    await expect.poll(() => editor2.getVersionLabel()).toBe(`Pinned to v${olderVersion}`);

    await editor2.discard();

    await detailsPanel1.expandSection("Custom Fields");
    await expect(detailsPanel1.customFields().fieldVersionLabel("Latest")).toBeVisible();

    const reopenedEditor = await detailsPanel1.customFields().editLinkField(fieldName);
    await expect.poll(() => reopenedEditor.getVersionLabel()).toBe("Latest");

    const pinDialog = await reopenedEditor.openVersionLockDialog(targetGlobalId);
    await pinDialog.selectVersion(olderVersion);
    await pinDialog.lockToSelectedVersion();
    await reopenedEditor.apply();

    await detailsPanel1.expandSection("Custom Fields");
    await expect(detailsPanel1.customFields().fieldVersionLabel(`Pinned to v${olderVersion}`)).toBeVisible();

    await detailsPanel1.saveEdit();

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.open(sampleName);

    const detailsPanel2 = pageInventory.detailsPanel;
    await detailsPanel2.expandSection("Custom Fields");
    await expect(detailsPanel2.customFields().fieldVersionLabel(`Pinned to v${olderVersion}`)).toBeVisible();

    await detailsPanel2.enterEditMode();
    await detailsPanel2.expandSection("Custom Fields");
    const editor3 = await detailsPanel2.customFields().editLinkField(fieldName);
    const dialog2 = await editor3.openVersionLockDialog(targetGlobalId);
    await dialog2.selectLatest();
    await dialog2.lockToSelectedVersion();
    await editor3.apply();
    await detailsPanel2.saveEdit();

    await detailsPanel2.expandSection("Custom Fields");
    await expect(detailsPanel2.customFields().fieldVersionLabel("Latest")).toBeVisible();
  });
});
