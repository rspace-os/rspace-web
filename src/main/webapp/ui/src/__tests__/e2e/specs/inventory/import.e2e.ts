import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";

const currentDir = dirname(fileURLToPath(import.meta.url));
const CONTAINERS_CSV = resolve(currentDir, "fixtures/containers_import_all_columns.csv");
const SAMPLES_CSV = resolve(currentDir, "fixtures/samples_import_with_id.csv");
const SUBSAMPLES_CSV = resolve(currentDir, "fixtures/subsamples_import_all_columns.csv");

async function expectContainerImportedToBench(page: Page, pageInventory: InventoryPage): Promise<void> {
  await page.goto("/inventory/search?resultType=CONTAINER");
  await pageInventory.isLoaded();
  await pageInventory.searchPanel.search("Container1");
  await pageInventory.searchPanel.open("Container1");
  const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
  await expect(breadcrumb).toContainText("My Bench");
  await expect(breadcrumb).toContainText("imported items");
}

test.describe(`Inventory CSV Import`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can import one CSV file after disabling global ID mapping`, async ({ pageInventory, page }) => {
    await pageInventory.open();
    await pageInventory.isLoaded();

    const importPage = await pageInventory.openCsvImport("Containers");
    await importPage.uploadCsv(CONTAINERS_CSV);
    await importPage.setColumnChecked("Parent container global ID", false);
    await importPage.clickImport();

    await expectContainerImportedToBench(page, pageInventory);
  });

  test(`As a user, I can import two CSV files connected by Import ID after disabling global ID mappings`, async ({
    pageInventory,
    page,
  }) => {
    await pageInventory.open();
    await pageInventory.isLoaded();

    const importPage = await pageInventory.openCsvImport("Containers");
    await importPage.uploadCsv(CONTAINERS_CSV);
    await importPage.setColumnChecked("Parent container global ID", false);

    await importPage.selectTab("SAMPLES");
    await importPage.uploadCsv(SAMPLES_CSV);
    await importPage.setColumnChecked("Parent Container Global ID", false);
    await importPage.clickImport();

    await expectContainerImportedToBench(page, pageInventory);

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search("BA 1");
    await expect(pageInventory.searchPanel.row("BA 1")).toBeVisible();
  });

  test(`As a user, I can import three CSV files connected by Import ID after disabling global ID mappings`, async ({
    pageInventory,
    page,
  }) => {
    await pageInventory.open();
    await pageInventory.isLoaded();

    const importPage = await pageInventory.openCsvImport("Containers");
    await importPage.uploadCsv(CONTAINERS_CSV);
    await importPage.setColumnChecked("Parent container global ID", false);

    await importPage.selectTab("SAMPLES");
    await importPage.uploadCsv(SAMPLES_CSV);
    await importPage.setColumnChecked("Parent Container Global ID", false);

    await importPage.selectTab("SUBSAMPLES");
    await importPage.uploadCsv(SUBSAMPLES_CSV);
    await importPage.setColumnChecked("Parent sample global ID", false);
    await importPage.clickImport();

    await expectContainerImportedToBench(page, pageInventory);

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search("BA 1");
    await expect(pageInventory.searchPanel.row("BA 1")).toBeVisible();

    await page.goto("/inventory/search?resultType=SUBSAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search("Subsample1");
    await expect(pageInventory.searchPanel.row("Subsample1")).toBeVisible();
  });

  test(`As a user, I can see Import disabled when both Import ID and Parent container global ID are unmapped`, async ({
    pageInventory,
  }) => {
    await pageInventory.open();
    await pageInventory.isLoaded();
    const importPage = await pageInventory.openCsvImport("Containers");
    await importPage.uploadCsv(CONTAINERS_CSV);

    await importPage.setColumnChecked("Parent container global ID", false);
    await importPage.setColumnChecked("Import ID", false);

    await expect(importPage.blockedWarning).toContainText("csv documents cannot be imported");
    await expect(importPage.importButton).toBeDisabled();

    await importPage.setColumnChecked("Import ID", true);
    await expect(importPage.importButton).toBeEnabled();
  });
});
