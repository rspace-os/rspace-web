import type { APIResponse, Page } from "@playwright/test";
import { expect } from "@playwright/test";
import type { ApiInventoryExportJob } from "@/__tests__/e2e/api/models/inventoryExport";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function downloadExport(page: Page, job: ApiInventoryExportJob): Promise<APIResponse> {
  expect(job.status).toBe("COMPLETED");
  expect(job.percentComplete).toBe(100);
  const downloadLink = job._links.find(({ rel }) => rel === "downloadLink")?.link;
  if (!downloadLink) throw new Error("Completed export did not include a download link.");
  const response = await page.request.get(downloadLink);
  expect(response.ok()).toBe(true);
  expect(Number(response.headers()["content-length"] ?? job.result?.size ?? 0)).toBeGreaterThan(0);
  return response;
}

test.describe(`Inventory export`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can export a sample as CSV`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-export-sample");

    await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 1 });

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.open(sampleName);

    const exportDialog = await pageInventory.detailsPanel.openExportDialog();
    await exportDialog.selectFileType("Single CSV");
    const job = await exportDialog.export();

    const download = await downloadExport(page, job);
    expect(await download.text()).toContain(sampleName);
  });

  test(`As a user, I can export all my Inventory data as CSV`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-export-all-csv");
    await clientInventory.createSample({ name: sampleName });

    await pageInventory.open();
    await pageInventory.isLoaded();

    const exportDialog = await pageInventory.openExportData();
    await exportDialog.selectFileType("Single CSV");
    const job = await exportDialog.export();

    const download = await downloadExport(page, job);
    expect(await download.text()).toContain(sampleName);
  });

  test(`As a user, I can export all my Inventory data as a ZIP bundle`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    await clientInventory.createContainer({ name: uniqueName("e2e-export-all-zip"), cType: "LIST" });

    await pageInventory.open();
    await pageInventory.isLoaded();

    const exportDialog = await pageInventory.openExportData();
    await exportDialog.selectFileType("ZIP Bundle");
    const job = await exportDialog.export();

    const download = await downloadExport(page, job);
    const bytes = await download.body();
    expect(bytes.subarray(0, 2).toString("ascii")).toBe("PK");
  });
});
