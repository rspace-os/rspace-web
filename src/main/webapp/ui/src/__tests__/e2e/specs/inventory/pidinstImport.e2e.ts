import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { IMPORTABLE_RID_PREFIX, importableHandle } from "@/__tests__/e2e/mocks/b2inst";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { importableDataCitePid } from "./pidinstTestData";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Inventory PIDINST Import`, { tag: [tags.INVENTORY] }, () => {
  test.describe(`B2INST provider`, () => {
    test.skip(INTEGRATION_MODE === "real", "no way to mint an immediately-importable B2INST PID without a curator");

    test(`As a user, I can import an instrument by searching a PIDINST registry for its PID`, async ({
      pageInventory,
      componentToasts,
      flowPidinstB2instConfig,
    }) => {
      void flowPidinstB2instConfig;
      const rid = uniqueName(IMPORTABLE_RID_PREFIX);
      const handle = importableHandle(rid);
      const expectedName = `E2E Import Target ${rid}`;

      await pageInventory.open();
      await pageInventory.isLoaded();
      let menu = await pageInventory.openCreateMenu();
      let dialog = await menu.openPidinstImport();
      await dialog.search(handle);
      await dialog.selectResult(expectedName);
      await expect(dialog.preview).toBeVisible();

      await expect
        .poll(() => dialog.previewFields())
        .toMatchObject({
          PID: handle,
          "Registry record": `https://b2inst-test.gwdg.de/records/${rid}`,
          Owners: "E2E Test Institution",
          Manufacturers: "E2E Instrument Co",
        });
      const importedGlobalId = `IN${await pageInventory.waitForNewInstrumentPage(() => dialog.clickImport())}`;
      await expect(componentToasts.byVariant("success", "Successfully imported the instrument.")).toBeVisible();
      await expect(pageInventory.detailsPanel.headingNamed(expectedName)).toBeVisible();

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.expandSection("Identifiers");

      await expect(identifiers.identifierLink(`https://hdl.handle.net/${handle}`)).toBeVisible();
      await identifiers.waitForType("B2INST");
      await identifiers.waitForState("Accepted");

      const customFields = pageInventory.detailsPanel.customFields();
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      await expect(customFields.fieldValue("Owner")).toHaveValue("E2E Test Institution");
      await expect(customFields.fieldValue("Manufacturer")).toHaveValue("E2E Instrument Co");

      menu = await pageInventory.openCreateMenu();
      dialog = await menu.openPidinstImport();
      await dialog.search(handle);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBannerFor(importedGlobalId)).toBeVisible();
      await dialog.clickImport();
      await expect(dialog.alreadyLinkedValidationWarning(importedGlobalId)).toBeVisible();
      await dialog.dismissValidationWarning(importedGlobalId);
      await dialog.close();
    });
  });

  test.describe(`DataCite provider`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
      "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
    );

    test(`As a user, I can import an instrument by searching a PIDINST registry for its PID`, async ({
      pageInventory,
      clientInventory,
      clientDataCite,
      componentToasts,
      flowPidinstDataciteConfig,
    }) => {
      void flowPidinstDataciteConfig;
      const importable = await importableDataCitePid({ clientInventory, clientDataCite }, "e2e-pidinst-import-real");
      const { pid: doi, name: expectedName } = importable;

      await pageInventory.open();
      await pageInventory.isLoaded();
      let menu = await pageInventory.openCreateMenu();
      let dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.preview).toBeVisible();

      await expect.poll(() => dialog.previewFields()).toMatchObject({ PID: doi });
      const importedGlobalId = `IN${await pageInventory.waitForNewInstrumentPage(() => dialog.clickImport())}`;
      await expect(componentToasts.byVariant("success", "Successfully imported the instrument.")).toBeVisible();
      await expect(pageInventory.detailsPanel.headingNamed(expectedName)).toBeVisible();

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.expandSection("Identifiers");

      await expect(identifiers.identifierLink(`https://doi.org/${doi}`)).toBeVisible();
      await identifiers.waitForType("DATACITE");
      await identifiers.waitForState("Findable");

      const customFields = pageInventory.detailsPanel.customFields();
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      await expect(customFields.fieldValue("Owner")).toHaveValue(importable.owner);
      await expect(customFields.fieldValue("Manufacturer")).toHaveValue(importable.manufacturer);

      menu = await pageInventory.openCreateMenu();
      dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBannerFor(importedGlobalId)).toBeVisible();
      await dialog.clickImport();
      await expect(dialog.alreadyLinkedValidationWarning(importedGlobalId)).toBeVisible();
      await dialog.dismissValidationWarning(importedGlobalId);
      await dialog.close();
    });
  });

  test(`As a user, the "From PIDINST registry" menu item is absent when no PIDINST provider is enabled`, {
    tag: tags.MOBILE,
  }, async ({ flowSysadminInventory, pageInventory }) => {
    env.assertGlobalMutationsAllowed("PIDINST import menu visibility with no provider enabled");

    const dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    for (const provider of ["DataCite", "B2INST"] as const) {
      await dialog.selectPidinstProvider(provider);
      if (await dialog.pidinstEnableToggle(provider).isChecked()) {
        await dialog.pidinstEnableToggle(provider).uncheck();
        await dialog.savePidinst();
      }
    }
    await dialog.close();

    expect(await pageInventory.openAndReadPidinstEnabled()).toBe(false);
    const menu = await pageInventory.openCreateMenu();
    await expect(menu.pidinstImportItem()).toHaveCount(0);
  });
});
