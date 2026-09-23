import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { importableDataCitePid } from "./pidinstTestData";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Inventory PIDINST unlink on delete`, { tag: [tags.INVENTORY] }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
    "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
  );

  test(`As a user, trashing an Instrument releases its linked PID so it can be imported again, and restoring the instrument does not bring the identifier back`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    flowPidinstDataciteConfig,
  }) => {
    void flowPidinstDataciteConfig;
    const { pid: doi, name: expectedName } = await importableDataCitePid(clientInventory, "e2e-pidinst-unlink-real");

    const original = await test.step("Given the PID is already imported as an Instrument", async () => {
      return clientInventory.importPidinst(doi);
    });

    await pageInventory.open();
    await pageInventory.isLoaded();

    await test.step("When searching for the linked PID again, then import is refused", async () => {
      const menu = await pageInventory.openCreateMenu();
      const dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBannerFor(original.globalId)).toBeVisible();
      await dialog.clickImport();
      await expect(dialog.alreadyLinkedValidationWarning(original.globalId)).toBeVisible();
      await dialog.dismissValidationWarning(original.globalId);
      await dialog.close();
    });

    await test.step("When the instrument is trashed, then the PID is released", async () => {
      await clientInventory.deleteInstrument(original.id);
    });

    const reimported = await test.step("Then the same PID can be imported again, as a new Instrument", async () => {
      const menu = await pageInventory.openCreateMenu();
      const dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBanner).toHaveCount(0);
      await dialog.clickImport();
      await expect(componentToasts.byVariant("success", "Successfully imported the instrument.")).toBeVisible();
      const id = await pageInventory.waitForNewInstrumentPage();
      return { id };
    });
    expect(reimported.id).not.toBe(original.id);

    await test.step("When the original instrument is restored, then its identifier does not come back", async () => {
      await clientInventory.restoreInstrument(original.id);
      await pageInventory.openInstrument(original.id);
      await pageInventory.detailsPanel.expandSection("Identifiers");
      await expect(pageInventory.detailsPanel.identifierCreateButton("PIDINST")).toBeEnabled();
      await expect(pageInventory.detailsPanel.identifiers().mentionsOf(doi)).toHaveCount(0);
    });
  });
});
