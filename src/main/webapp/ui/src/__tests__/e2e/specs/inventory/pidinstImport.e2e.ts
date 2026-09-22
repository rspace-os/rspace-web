import { expect } from "@playwright/test";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { IMPORTABLE_RID_PREFIX, importableHandle } from "@/__tests__/e2e/mocks/b2inst";
import { IMPORTABLE_DOI_PREFIX, IMPORTABLE_DOI_SUFFIX_PREFIX } from "@/__tests__/e2e/mocks/datacite";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

async function importableDataCitePid(clientInventory: InventoryClient): Promise<{ pid: string; name: string }> {
  if (INTEGRATION_MODE !== "real") {
    const suffix = uniqueName(IMPORTABLE_DOI_SUFFIX_PREFIX);
    return { pid: `${IMPORTABLE_DOI_PREFIX}/${suffix}`, name: `E2E Import Target ${suffix}` };
  }
  const name = uniqueName("e2e-pidinst-import-real");
  const throwaway = await clientInventory.createInstrument({ name });
  const info = await clientInventory.registerIdentifier({ parentGlobalId: throwaway.globalId });
  await clientInventory.publishIdentifier(info.id);

  await clientInventory.deleteInstrument(throwaway.id);
  return { pid: info.doi, name };
}

test.describe(`Inventory PIDINST Import`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test.describe(`B2INST provider`, () => {
    test.skip(INTEGRATION_MODE === "real", "no way to mint an immediately-importable B2INST PID without a curator");

    test(`As a user, I can import an instrument by searching a PIDINST registry for its PID`, async ({
      pageInventory,
      componentToasts,
      flowPidinstB2instConfig,
      page,
    }, testInfo) => {
      testInfo.skip(
        testInfo.project.name === "mobile",
        "Confirmed Playwright-mobile-emulation-only artifact, not a real defect",
      );
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

      await expect(dialog.previewField("PID")).toHaveText(handle);
      await expect(dialog.previewField("Registry record")).toHaveText(`https://b2inst-test.gwdg.de/records/${rid}`);
      await expect(dialog.previewField("Owners")).toHaveText("E2E Test Institution");
      await expect(dialog.previewField("Manufacturers")).toHaveText("E2E Instrument Co");
      await dialog.clickImport();

      await expect(componentToasts.byVariant("success", "Successfully imported the instrument.")).toBeVisible();
      await pageInventory.waitForNewInstrumentPage();
      await expect(page.getByRole("heading", { name: expectedName })).toBeVisible();

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.expandSection("Identifiers");

      await expect(identifiers.identifierLink(`https://hdl.handle.net/${handle}`)).toBeVisible();
      await identifiers.waitForType("B2INST");
      await identifiers.waitForState("Accepted");

      const customFields = pageInventory.detailsPanel.section("Custom Fields");
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      await expect(customFields.getByRole("group", { name: "Owner", exact: true }).getByRole("textbox")).toHaveValue(
        "E2E Test Institution",
      );
      await expect(
        customFields.getByRole("group", { name: "Manufacturer", exact: true }).getByRole("textbox"),
      ).toHaveValue("E2E Instrument Co");

      menu = await pageInventory.openCreateMenu();
      dialog = await menu.openPidinstImport();
      await dialog.search(handle);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBanner).toBeVisible();
      await dialog.clickImport();
      await expect(dialog.alreadyLinkedValidationWarning).toBeVisible();
      await dialog.dismissValidationWarning();
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
      componentToasts,
      flowPidinstDataciteConfig,
      page,
    }, testInfo) => {
      testInfo.skip(
        testInfo.project.name === "mobile",
        "Confirmed Playwright-mobile-emulation-only artifact, not a real defect",
      );
      void flowPidinstDataciteConfig;
      const { pid: doi, name: expectedName } = await importableDataCitePid(clientInventory);

      await pageInventory.open();
      await pageInventory.isLoaded();
      let menu = await pageInventory.openCreateMenu();
      let dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.preview).toBeVisible();

      await expect(dialog.previewField("PID")).toHaveText(doi);
      await dialog.clickImport();

      await expect(componentToasts.byVariant("success", "Successfully imported the instrument.")).toBeVisible();
      await pageInventory.waitForNewInstrumentPage();
      await expect(page.getByRole("heading", { name: expectedName })).toBeVisible();

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.expandSection("Identifiers");

      await expect(identifiers.identifierLink(`https://doi.org/${doi}`)).toBeVisible();
      await identifiers.waitForType("DATACITE");
      await identifiers.waitForState("Findable");

      if (INTEGRATION_MODE !== "real") {
        const customFields = pageInventory.detailsPanel.section("Custom Fields");
        await pageInventory.detailsPanel.expandSection("Custom Fields");
        await expect(customFields.getByRole("group", { name: "Owner", exact: true }).getByRole("textbox")).toHaveValue(
          "E2E Test Institution",
        );
        await expect(
          customFields.getByRole("group", { name: "Manufacturer", exact: true }).getByRole("textbox"),
        ).toHaveValue("E2E Instrument Co");
      }

      menu = await pageInventory.openCreateMenu();
      dialog = await menu.openPidinstImport();
      await dialog.search(doi);
      await dialog.selectResult(expectedName);
      await expect(dialog.alreadyLinkedBanner).toBeVisible();
      await dialog.clickImport();
      await expect(dialog.alreadyLinkedValidationWarning).toBeVisible();
      await dialog.dismissValidationWarning();
      await dialog.close();
    });
  });

  test(`As a user, the "From PIDINST registry" menu item is absent when no PIDINST provider is enabled`, async ({
    flowSysadminInventory,
    pageInventory,
  }) => {
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

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    expect(await menu.hasPidinstImport()).toBe(false);
  });
});
