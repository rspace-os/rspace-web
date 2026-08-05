import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Inventory Print Options dialog`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can print an item without a registered IGSN`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-print-sample");

    await clientInventory.createSample({ name: sampleName });

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.selectItem(sampleName);

    const dialog = await test.step("When I open Print Barcode", async () => {
      return pageInventory.searchPanel.batchActions.openPrintOptionsDialog();
    });

    await dialog.selectIdentifierType("IGSN ID");
    await expect(dialog.message("IGSN ID")).toBeVisible();

    await dialog.selectIdentifierType("Global ID");
    await dialog.selectPrinterType("Label Printer");

    await expect(dialog.message("Print one label per sticker")).toBeVisible();
    await expect(dialog.radio("Small")).toHaveCount(0);
    await expect(dialog.radio("Large")).toHaveCount(0);

    await dialog.selectPrinterType("Standard Printer");
    await dialog.selectPrintLayout("Full");
    await dialog.selectPrintSize("Large");

    await expect(dialog.barcodePreview).toBeVisible();
    await expect.poll(() => dialog.printButtonText()).toContain("1");
  });

  test.describe(`Registered IGSN`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
      "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
    );

    test(`As a user, I can print an item with a registered IGSN`, async ({
      pageInventory,
      clientInventory,
      flowIgsnConfig,
      page,
    }) => {
      void flowIgsnConfig;
      const sampleName = uniqueName("e2e-print-igsn-sample");

      const created = await clientInventory.createSample({
        name: sampleName,
        barcodes: [
          { data: "https://pangolin8086.researchspace.com", format: "QR", description: "pangolin8086" },
          { data: "https://www.wikipedia.org/", format: "QR", description: "wikipedia" },
        ],
      });
      await clientInventory.registerIdentifier({ parentGlobalId: created.globalId });

      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.searchPanel.search(sampleName);
      await pageInventory.searchPanel.selectItem(sampleName);

      const dialog = await pageInventory.searchPanel.batchActions.openPrintOptionsDialog();
      await dialog.selectIdentifierType("IGSN ID");
      await expect(dialog.message("IGSN ID")).not.toBeVisible();
      await dialog.cancel();

      const printAllDialog =
        await test.step("When I open the sample's own Barcodes section and Print All", async () => {
          await pageInventory.searchPanel.open(sampleName);
          return pageInventory.detailsPanel.openPrintAllBarcodesDialog();
        });

      await expect.poll(() => printAllDialog.printButtonText()).toContain("3");
      await expect(printAllDialog.message("IGSN ID")).not.toBeVisible();
    });
  });
});
