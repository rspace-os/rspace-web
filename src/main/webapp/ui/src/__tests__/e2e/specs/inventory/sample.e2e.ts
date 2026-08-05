import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

const currentDir = dirname(fileURLToPath(import.meta.url));
const SAMPLE_IMAGE = resolve(currentDir, "fixtures/add_sample_image.png");
const NO_EDIT_PERMISSION = "You do not have permission to edit this sample.";

test.describe(`Inventory Samples`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can create a sample with details, tag, and temperature`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    const sampleName = uniqueName("e2e-sample");
    const tag = uniqueName("e2e-tag");
    const minTemp = "-15";
    const today = String(new Date().getDate());

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();

    const form = await test.step("When I open the New Sample form and fill in its fields", async () => {
      const menu = await pageInventory.openCreateMenu();
      const sampleForm = await menu.newSample();
      await sampleForm.uploadImage(SAMPLE_IMAGE);
      await sampleForm.expandSection("Details");
      await sampleForm.setExpiryDateToday(today);
      await sampleForm.setStorageTemperatureMin(minTemp);
      await sampleForm.setDescription("Test");
      await sampleForm.addTag(tag);
      return sampleForm;
    });

    await expect.poll(() => form.expiryDateText()).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    await form.fillName(sampleName);
    await form.setWithSubsamples(3);
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.detailsPanel.expandSection("Details");
    const details = pageInventory.detailsPanel.section("Details");
    await expect(details.getByText(tag, { exact: true })).toBeVisible();
    await expect(details).toContainText(`Between ${minTemp}`);
  });

  test(`As a user, I can create a new Sample Template from an existing Sample`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    page,
  }) => {
    const sampleName = uniqueName("e2e-sample-for-template");
    const templateName = uniqueName("e2e-template-from-sample");

    await clientInventory.createSample({ name: sampleName });

    const dialog = await test.step("When I open its Create dialog", async () => {
      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.isLoaded();
      await pageInventory.searchPanel.search(sampleName);
      await pageInventory.searchPanel.open(sampleName);
      return pageInventory.detailsPanel.openCreateItemDialog();
    });

    await dialog.createTemplate(templateName);

    await expect(componentToasts.byVariant("success", "Template created successfully")).toBeVisible();

    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(templateName);
    await expect(pageInventory.searchPanel.row(templateName)).toBeVisible();
  });

  test(`As a sysadmin, I can create a Template—but not new or split Subsamples—from a Sample I don't own`, async ({
    browser,
    browserContextOptions,
    clientInventory,
  }) => {
    const sampleName = uniqueName("e2e-sample-sysadmin-denied");
    await clientInventory.createSample({ name: sampleName });

    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    try {
      const page = await ctx.newPage();
      const inventoryPage = new InventoryPage(page);

      const dialog =
        await test.step("Given I am logged in as sysadmin and open the Create dialog for a sample I don't own", async () => {
          await page.goto("/inventory/search?resultType=SAMPLE");
          await inventoryPage.isLoaded();
          await inventoryPage.searchPanel.search(sampleName);
          await inventoryPage.searchPanel.open(sampleName);
          return inventoryPage.detailsPanel.openCreateItemDialog();
        });

      await expect(dialog.newSubsamplesOption()).toBeDisabled();
      await expect(dialog.newSubsamplesOption()).toHaveAccessibleDescription(NO_EDIT_PERMISSION);
      await expect(dialog.splitSampleOption()).toBeDisabled();
      await expect(dialog.splitSampleOption()).toHaveAccessibleDescription(NO_EDIT_PERMISSION);

      await expect(dialog.templateOption()).toBeEnabled();
      await expect(dialog.templateOption()).not.toHaveAccessibleDescription(NO_EDIT_PERMISSION);
    } finally {
      await ctx.close();
    }
  });

  test(`As a user, I can create a Sample with custom fields inherited from a Template`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    const templateName = uniqueName("e2e-sample-fields-template");
    const sampleName = uniqueName("e2e-sample-fields-from-template");
    const fieldName = "Incubation Temperature";
    const fieldValue = "5";

    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    const templateForm = await menu.newSampleTemplate();
    await templateForm.fillName(templateName);
    await templateForm.expandSection("Custom Fields");
    await templateForm.addCustomField("Number", fieldName);
    await templateForm.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const form = await test.step("When I create a Sample from that template", async () => {
      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.isLoaded();
      const menu = await pageInventory.openCreateMenu();
      const sampleForm = await menu.newSample();
      await sampleForm.selectTemplate(templateName);
      return sampleForm;
    });

    await form.expandSection("Custom Fields");
    await expect(form.section("Custom Fields").getByRole("group", { name: fieldName })).toBeVisible();

    await form
      .section("Custom Fields")
      .getByRole("group", { name: fieldName })
      .getByRole("spinbutton", { name: fieldName })
      .fill(fieldValue);
    await form.fillName(sampleName);
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.detailsPanel.expandSection("Custom Fields");
    const details = pageInventory.detailsPanel.section("Custom Fields");
    await expect(details.getByRole("group", { name: fieldName }).getByRole("spinbutton")).toHaveValue(fieldValue);
  });

  test(`As a user, I can split a Sample's Subsample into multiple new Subsamples`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const sampleName = uniqueName("e2e-sample-split");

    const sample = await test.step("Given a Sample exists", async () => {
      return clientInventory.createSample({ name: sampleName });
    });

    const dialog = await test.step("When I open its Create dialog", async () => {
      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.isLoaded();
      await pageInventory.searchPanel.search(sampleName);
      await pageInventory.searchPanel.open(sampleName);
      return pageInventory.detailsPanel.openCreateItemDialog();
    });

    await dialog.splitIntoSubsamples(3);

    await page.goto(`/inventory/search?parentGlobalId=${sample.globalId}`);
    await pageInventory.isLoaded();
    await expect.poll(() => pageInventory.searchPanel.rowCount()).toBe(3);
  });

  test(`As a user, I can duplicate a single Sample`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-duplicate-single");

    await clientInventory.createSample({ name: sampleName });

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.open(sampleName);

    await (await pageInventory.detailsPanel.duplicateControl()).click();

    await expect(pageInventory.detailsPanel.heading).toContainText(`${sampleName}_COPY`);

    await pageInventory.searchPanel.ensureVisible();
    await expect.poll(() => pageInventory.searchPanel.rowCount()).toBe(2);
    await expect(pageInventory.searchPanel.row(sampleName, { exact: true })).toBeVisible();
    await expect(pageInventory.searchPanel.row(`${sampleName}_COPY`)).toBeVisible();
  });

  test(`As a user, I can duplicate multiple selected Samples at once`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const baseName = uniqueName("e2e-duplicate-multi");
    const sampleNames = [0, 1].map((i) => `${baseName}-${i}`);

    for (const name of sampleNames) {
      await clientInventory.createSample({ name });
    }

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(baseName);
    await pageInventory.searchPanel.selectAll();
    await pageInventory.searchPanel.batchActions.clickMoreAction("Duplicate");

    await expect.poll(() => pageInventory.searchPanel.rowCount()).toBe(4);
    for (const name of sampleNames) {
      await expect(pageInventory.searchPanel.row(name, { exact: true })).toBeVisible();
      await expect(pageInventory.searchPanel.row(`${name}_COPY`)).toBeVisible();
    }
  });

  test.describe(`Batch editing`, () => {
    test(`As a user, I can batch-edit multiple Samples at once`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      page,
    }) => {
      const baseName = uniqueName("e2e-sample-batch-edit");
      const sampleNames = [0, 1, 2].map((i) => `${baseName}-${i}`);
      const editedName = uniqueName("e2e-sample-batch-edited");

      for (const name of sampleNames) {
        await clientInventory.createSample({ name });
      }

      const form = await test.step("When I select all 3 and open Batch Edit", async () => {
        await page.goto("/inventory/search?resultType=SAMPLE");
        await pageInventory.isLoaded();
        await pageInventory.searchPanel.search(baseName);
        for (const name of sampleNames) {
          await pageInventory.searchPanel.selectItem(name);
        }
        return pageInventory.searchPanel.batchActions.openBatchEdit();
      });

      await form.fillName(editedName);
      await form.save();

      await expect(componentToasts.byVariant("success", "successfully updated")).toBeVisible();

      await pageInventory.searchPanel.search(editedName);
      await expect(pageInventory.searchPanel.row(editedName)).toHaveCount(3);
    });
  });

  test(`As a user, I can add a Note to a Subsample`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-sample-note");
    const noteContent = uniqueName("e2e-note-content");

    const subsampleName = await test.step("Given a Sample exists", async () => {
      const sample = await clientInventory.createSample({ name: sampleName });
      return sample.subSamples[0].name;
    });

    await page.goto("/inventory/search?resultType=SUBSAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(subsampleName);
    await pageInventory.searchPanel.open(subsampleName);
    await pageInventory.detailsPanel.expandSection("Notes");
    await pageInventory.detailsPanel.notes().addNote(noteContent);

    await expect(pageInventory.detailsPanel.notes().noteContent(noteContent)).toBeVisible();
  });
});
