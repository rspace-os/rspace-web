import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Inventory PIDINST Settings UI`, { tag: [tags.INVENTORY] }, () => {
  test(`As a sysadmin, I can enable PIDINST via DataCite or B2INST, but not both at once`, async ({
    flowSysadminInventory,
  }) => {
    env.assertGlobalMutationsAllowed("PIDINST Settings UI provider switch");

    let dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    await dialog.selectPidinstProvider("DataCite");
    await dialog.fillPidinstDatacite({
      server: "Test",
      username: "e2e-settings-ui-dummy",
      password: "e2e-settings-ui-dummy",
      repositoryPrefix: "10.99999",
    });
    await dialog.pidinstEnableToggle("DataCite").check();
    await dialog.savePidinst();
    await expect(dialog.pidinstEnableToggle("DataCite")).toBeChecked();
    await dialog.close();

    dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    await expect(dialog.pidinstTab).toContainText("DataCite Connected");

    await dialog.selectPidinstProvider("B2INST");
    await dialog.fillPidinstB2Inst({
      serverUrl: "https://example.com",
      communityId: "e2e-settings-ui-dummy",
      token: "e2e-settings-ui-dummy",
    });
    await dialog.pidinstEnableToggle("B2INST").check();
    await expect(
      dialog.root.getByText("Only one PIDINST provider can be enabled").filter({ visible: true }),
    ).toBeVisible();
    await expect(dialog.root.getByRole("button", { name: "Save", exact: true })).toBeDisabled();

    await dialog.selectPidinstProvider("DataCite");
    await dialog.pidinstEnableToggle("DataCite").uncheck();
    await dialog.savePidinst();

    await dialog.selectPidinstProvider("B2INST");
    await expect(dialog.pidinstEnableToggle("B2INST")).toBeChecked();
    await dialog.savePidinst();
    await expect(dialog.pidinstEnableToggle("B2INST")).toBeChecked();
    await dialog.close();

    dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    await expect(dialog.pidinstTab).toContainText("B2INST Connected");
    await dialog.selectPidinstProvider("DataCite");
    await expect(dialog.pidinstEnableToggle("DataCite")).not.toBeChecked();
    await dialog.close();
  });

  test(`As a user, the Create PIDINST button is disabled when no provider is configured`, async ({
    flowSysadminInventory,
    pageInventory,
    clientInventory,
    page,
  }) => {
    env.assertGlobalMutationsAllowed("PIDINST Settings UI disable all providers");

    let dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    await dialog.selectPidinstProvider("DataCite");
    if (await dialog.pidinstEnableToggle("DataCite").isChecked()) {
      await dialog.pidinstEnableToggle("DataCite").uncheck();
      await dialog.savePidinst();
    }
    await dialog.close();

    dialog = await flowSysadminInventory.openIdentifierSettings();
    await dialog.openPidinstTab();
    await dialog.selectPidinstProvider("B2INST");
    if (await dialog.pidinstEnableToggle("B2INST").isChecked()) {
      await dialog.pidinstEnableToggle("B2INST").uncheck();
      await dialog.savePidinst();
    }
    await dialog.close();

    const instrumentName = uniqueName("e2e-pidinst-disabled-instrument");
    await clientInventory.createInstrument({ name: instrumentName });

    await page.goto(`/inventory/search?resultType=INSTRUMENT`);
    await pageInventory.searchPanel.search(instrumentName);
    await pageInventory.searchPanel.open(instrumentName);
    await pageInventory.detailsPanel.expandSection("Identifiers");

    await expect(pageInventory.detailsPanel.identifierCreateButton("PIDINST")).toBeDisabled();
  });
});
