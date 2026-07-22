import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN, USERS } from "@/__tests__/e2e/users";

const currentDir = dirname(fileURLToPath(import.meta.url));
const PREVIEW_IMAGE = resolve(currentDir, "fixtures/container_preview.png");
const BACKGROUND_IMAGE = resolve(currentDir, "fixtures/container_background.png");

test.describe(`Inventory Containers`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can create a sample inside a list container`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-sample-parent");
    const sampleName = uniqueName("e2e-container-sample");

    const container = await clientInventory.createContainer({ name: containerName, cType: "LIST" });

    await pageInventory.openRecord("CONTAINER", containerName);
    const dialog = await pageInventory.detailsPanel.openCreateItemDialog();
    const sample = await dialog.chooseSample();

    await expect(sample.root.getByRole("heading", { level: 2, name: "New Sample" })).toBeVisible();

    await sample.fillName(sampleName);
    await sample.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.openSearch("SUBSAMPLE", container.globalId);
    await expect(pageInventory.searchPanel.row(`${sampleName}.01`)).toBeVisible();
  });

  test(`As a user, I can create a container inside a list container`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-container-parent");
    const newContainerName = uniqueName("e2e-container-nested");

    const container = await clientInventory.createContainer({ name: containerName, cType: "LIST" });

    await pageInventory.openRecord("CONTAINER", containerName);
    const dialog = await pageInventory.detailsPanel.openCreateItemDialog();
    const newContainer = await dialog.chooseContainer();

    await expect(newContainer.root.getByRole("heading", { level: 2, name: "New Container" })).toBeVisible();

    await newContainer.fillName(newContainerName);
    await newContainer.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.openSearch("CONTAINER", container.globalId);
    await expect(pageInventory.searchPanel.row(newContainerName)).toBeVisible();
  });

  test(`As a user, I can create a sample and a container from a grid container, at a chosen location`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    test.slow();
    const containerName = uniqueName("e2e-container-grid-parent");
    const sampleName = uniqueName("e2e-container-grid-sample");
    const newContainerName = uniqueName("e2e-container-grid-nested");

    const container = await test.step("Given a 2x2 grid container exists", () =>
      clientInventory.createContainer({
        name: containerName,
        cType: "GRID",
        gridLayout: { columnsNumber: 2, rowsNumber: 2 },
      }));

    await pageInventory.openRecord("CONTAINER", containerName);
    const dialog1 = await pageInventory.detailsPanel.openCreateItemDialog();
    const sample = await dialog1.chooseSample({ location: 1 });

    await expect(sample.root.getByRole("heading", { level: 2, name: "New Sample" })).toBeVisible();

    await sample.fillName(sampleName);
    await sample.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.openSearch("SUBSAMPLE", container.globalId);
    await expect(pageInventory.searchPanel.row(`${sampleName}.01`)).toBeVisible();

    await pageInventory.openRecord("CONTAINER", containerName);
    const dialog2 = await pageInventory.detailsPanel.openCreateItemDialog();
    const newContainer = await dialog2.chooseContainer({ location: 2 });

    await expect(newContainer.root.getByRole("heading", { level: 2, name: "New Container" })).toBeVisible();

    await newContainer.fillName(newContainerName);
    await newContainer.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.openSearch("CONTAINER", container.globalId);
    await expect(pageInventory.searchPanel.row(newContainerName)).toBeVisible();
  });

  test(`As a user, I can move subsamples into a list container`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-move-target");
    const sampleName = uniqueName("e2e-container-move-sample");

    const container = await test.step("Given a list container and a sample with 3 subsamples exist", async () => {
      const c = await clientInventory.createContainer({ name: containerName, cType: "LIST" });
      await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 3 });
      return c;
    });

    await pageInventory.openSearch("SUBSAMPLE");
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.selectItem(`${sampleName}.01`);
    await pageInventory.searchPanel.selectItem(`${sampleName}.02`);
    const moveDialog = await pageInventory.searchPanel.batchActions.openMoveDialog();
    await moveDialog.search(containerName);
    await moveDialog.selectDestination(containerName);
    await moveDialog.confirmMove();

    await expect(componentToasts.byText("Subsamples successfully moved.")).toBeVisible();

    await pageInventory.openSearch("SUBSAMPLE", container.globalId);
    await expect(pageInventory.searchPanel.statusText).toContainText("2 subsamples found.");
    await expect(pageInventory.searchPanel.row(`${sampleName}.01`)).toBeVisible();
    await expect(pageInventory.searchPanel.row(`${sampleName}.02`)).toBeVisible();
    await expect(pageInventory.searchPanel.row(`${sampleName}.03`)).not.toBeVisible();
  });

  test(`As a sysadmin, I can see creation unavailable for a container I don't own`, async ({
    clientInventory,
    pageInventoryForUser,
  }) => {
    const containerName = uniqueName("e2e-container-sysadmin-denied");
    await clientInventory.createContainer({
      name: containerName,
      cType: "GRID",
      gridLayout: { columnsNumber: 3, rowsNumber: 3 },
    });

    const inventoryPage = await pageInventoryForUser(SYSADMIN);
    await inventoryPage.openRecord("CONTAINER", containerName);
    const dialog = await inventoryPage.detailsPanel.openCreateItemDialog();

    await expect(dialog.containerOption).toBeDisabled();
    await expect(dialog.sampleOption).toBeDisabled();
    await expect(dialog.root).toContainText("You do not have permission");
  });

  test(`As a user, I can transfer a container to another user`, async ({
    pageInventory,
    clientInventory,
    pageInventoryForUser,
  }) => {
    const containerName = uniqueName("e2e-container-transfer");

    await clientInventory.createContainer({ name: containerName, cType: "LIST" });

    await pageInventory.openRecord("CONTAINER", containerName);
    const transferDialog = await pageInventory.detailsPanel.openTransferDialog();
    await transferDialog.selectRecipient(USERS.user6f.username);
    await transferDialog.confirmTransfer();

    const recipientInventory = await pageInventoryForUser(USERS.user6f);
    await recipientInventory.openSearch("CONTAINER");
    await recipientInventory.searchPanel.search(containerName);
    await expect(recipientInventory.searchPanel.row(containerName)).toBeVisible();
  });

  test(`As a user, I can trash and restore a container`, async ({ pageInventory, clientInventory, page }) => {
    const containerName = uniqueName("e2e-container-trash");

    await clientInventory.createContainer({ name: containerName, cType: "LIST" });

    await pageInventory.openSearch("CONTAINER");
    await pageInventory.searchPanel.search(containerName);
    await pageInventory.searchPanel.selectItem(containerName);
    await pageInventory.searchPanel.batchActions.clickMoreAction("Trash");

    await pageInventory.searchPanel.filterChip("Status").click();

    await page.getByRole("menuitem", { name: "In Trash", exact: true }).click();
    await pageInventory.searchPanel.search(containerName);
    await expect(pageInventory.searchPanel.row(containerName)).toBeVisible();

    await pageInventory.searchPanel.selectItem(containerName);
    await pageInventory.searchPanel.batchActions.restore();

    await pageInventory.searchPanel.filterChip("Status").click();
    await page.getByRole("menuitem", { name: "Current", exact: true }).click();
    await pageInventory.searchPanel.search(containerName);
    await expect(pageInventory.searchPanel.row(containerName)).toBeVisible();
  });

  test(`As a user, I can see Grid container contents as a grid by default and switch to List view`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const containerName = uniqueName("e2e-container-gridview");

    await clientInventory.createContainer({
      name: containerName,
      cType: "GRID",
      gridLayout: { columnsNumber: 3, rowsNumber: 3 },
    });

    const locationsAndContent = await test.step("When I open it", async () => {
      await pageInventory.openRecord("CONTAINER", containerName);
      return pageInventory.detailsPanel.section("Locations and Content");
    });

    await expect(locationsAndContent.getByRole("grid")).toBeVisible();

    await locationsAndContent.getByLabel("Change view").click();
    await page.getByRole("menuitem", { name: "List View", exact: true }).click();

    await expect(locationsAndContent.getByRole("grid")).not.toBeVisible();
    await expect(locationsAndContent.getByText("Empty Container")).toBeVisible();
  });

  test(`As a user, I can customize a grid container's row and column axis labels`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-axislabels");

    const form = await pageInventory.openNewContainerForm();
    await form.fillName(containerName);
    await form.setType("Grid");
    await form.setGridDimensions(2, 2);
    await form.expandConfigureGridLabels();
    await form.setRowLabelType("123");
    await form.setColumnLabelType("ABC");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const grid = pageInventory.detailsPanel.section("Locations and Content").getByRole("grid");
    await expect(grid.getByRole("rowheader").first()).toHaveText("1");
    await expect(grid.getByRole("columnheader").nth(1)).toHaveText("A");
  });

  test(`As a user, I can create a list container with a description, tag, custom field, and preview image, then edit its attachments`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-full");

    const form = await pageInventory.openNewContainerForm();
    await form.fillName(containerName);
    await form.expandSection("Details");
    await form.setDescription("e2e container description");
    await form.addTag("e2e-container-tag");
    await form.uploadPreviewImage(PREVIEW_IMAGE);
    await form.expandSection("Custom Fields");
    await form.customFields().addNewTextField("FieldName");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.searchPanel.search(containerName);
    await pageInventory.searchPanel.open(containerName);
    await pageInventory.detailsPanel.expandSection("Details");
    await expect(pageInventory.detailsPanel.section("Details")).toContainText("e2e container description");
    await expect(pageInventory.detailsPanel.section("Details")).toContainText("e2e-container-tag");
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await expect(pageInventory.detailsPanel.section("Custom Fields")).toContainText("FieldName");

    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Attachments");
    await pageInventory.detailsPanel.attachments().upload(PREVIEW_IMAGE);
    await pageInventory.detailsPanel.attachments().setAsPreviewImage("container_preview.png");
    await pageInventory.detailsPanel.saveEdit();

    await expect(componentToasts.byVariant("success", "updated successfully")).toBeVisible();
  });

  test(`As a user, I can create a grid container using a common dimension preset`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-preset");

    const form = await pageInventory.openNewContainerForm();
    await form.fillName(containerName);
    await form.setType("Grid");
    await form.chooseCommonGridDimensions("12 well plate");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await pageInventory.searchPanel.search(containerName);
    await pageInventory.searchPanel.open(containerName);
    const grid = pageInventory.detailsPanel.section("Locations and Content").getByRole("grid");
    await expect(grid.getByRole("gridcell")).toHaveCount(12);
  });

  test(`As a user, I can create a visual container and mark locations on its image`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-visual");

    const form = await pageInventory.openNewContainerForm();
    await form.fillName(containerName);
    await form.setType("Visual");
    await form.uploadLocationsImage(BACKGROUND_IMAGE);
    const editLocations = await form.openEditLocations();

    await expect.poll(() => editLocations.markerCount()).toBe(0);

    await editLocations.addMarker(0.25, 0.25);
    await editLocations.addMarker(0.5, 0.5);
    await editLocations.addMarker(0.75, 0.75);

    await expect.poll(() => editLocations.markerCount()).toBe(3);

    await editLocations.done();

    const reopened = await form.openEditLocations();
    await expect.poll(() => reopened.markerCount()).toBe(3);
    await reopened.done();

    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
  });

  test(`As a user, I can move subsamples into a Visual container`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const containerName = uniqueName("e2e-container-visual-move");
    const sampleName = uniqueName("e2e-visual-move-sample");
    const subsampleCount = 5;
    const fractions: Array<[number, number]> = [
      [0.15, 0.15],
      [0.5, 0.15],
      [0.85, 0.15],
      [0.15, 0.5],
      [0.5, 0.5],
      [0.85, 0.5],
      [0.5, 0.85],
    ];

    await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: subsampleCount });

    const form = await pageInventory.openNewContainerForm();
    await form.fillName(containerName);
    await form.setType("Visual");
    await form.uploadLocationsImage(BACKGROUND_IMAGE);
    const editLocations = await form.openEditLocations();
    for (const [x, y] of fractions) {
      await editLocations.addMarker(x, y);
    }
    await expect.poll(() => editLocations.markerCount()).toBe(fractions.length);
    await editLocations.done();
    await form.save();

    await pageInventory.openSearch("SUBSAMPLE");

    await pageInventory.searchPanel.search(sampleName);
    for (let i = 1; i <= subsampleCount; i++) {
      await pageInventory.searchPanel.selectItem(`${sampleName}.0${i}`);
    }
    const moveDialog = await pageInventory.searchPanel.batchActions.openMoveDialog();
    await moveDialog.search(containerName);
    await moveDialog.selectDestination(containerName);
    await moveDialog.selectLocations(subsampleCount);
    await moveDialog.confirmMove();

    await expect(componentToasts.byText("Subsamples successfully moved.")).toBeVisible();

    await pageInventory.openRecord("CONTAINER", containerName);
    await expect
      .poll(() => pageInventory.detailsPanel.emptyLocationCount(), {
        timeout: 15_000,
      })
      .toBe(fractions.length - subsampleCount);
  });
});
