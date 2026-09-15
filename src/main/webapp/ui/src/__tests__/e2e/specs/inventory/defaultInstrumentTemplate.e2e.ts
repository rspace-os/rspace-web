import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

// templete seeded this system-wide
const DEFAULT_TEMPLATE_NAME = "Instrument (PIDINST 1.0)";

async function openDefaultTemplate(page: Page, pageInventory: InventoryPage) {
  await page.goto("/inventory/search?resultType=INSTRUMENT_TEMPLATE");
  await pageInventory.isLoaded();
  await pageInventory.searchPanel.search(DEFAULT_TEMPLATE_NAME);
  await pageInventory.searchPanel.open(DEFAULT_TEMPLATE_NAME, { exact: true });
}

test.describe(`Default PIDINST Instrument Template`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can read the default PIDINST Instrument Template but not edit it`, async ({
    pageInventory,
    page,
  }) => {
    await openDefaultTemplate(page, pageInventory);

    await expect(pageInventory.detailsPanel.heading).toContainText(DEFAULT_TEMPLATE_NAME);
    await expect(pageInventory.detailsPanel.action("Edit")).toBeDisabled();
    await expect(await pageInventory.detailsPanel.duplicateControl()).toBeEnabled();
  });

  test.describe(`duplication`, () => {
    let copyId: number | undefined;

    test.afterEach(async ({ clientInventory }) => {
      if (copyId !== undefined) await clientInventory.deleteInstrumentTemplate(copyId);
      copyId = undefined;
    });

    test(`As a user, duplicating the default template gives me an editable copy`, async ({
      pageInventory,
      page,
      clientInventory,
    }) => {
      await openDefaultTemplate(page, pageInventory);
      await (await pageInventory.detailsPanel.duplicateControl()).click();
      await expect(pageInventory.detailsPanel.heading).toContainText(`${DEFAULT_TEMPLATE_NAME}_COPY`);
      await expect(pageInventory.detailsPanel.action("Edit")).toBeEnabled();

      copyId = await clientInventory.findInstrumentTemplateIdByExactName(`${DEFAULT_TEMPLATE_NAME}_COPY`);
    });
  });

  test(`As a user, I can create an Instrument from the default PIDINST Instrument Template`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    const instrumentName = uniqueName("e2e-instrument-from-default-template");

    await openDefaultTemplate(page, pageInventory);
    const dialog = await pageInventory.detailsPanel.openCreateItemDialog();
    const instrumentForm = await dialog.chooseInstrument();
    await instrumentForm.fillName(instrumentName);

    await instrumentForm.expandSection("Custom Fields");
    await instrumentForm.root.getByRole("textbox", { name: "Owner", exact: true }).fill("e2e-owner");
    await instrumentForm.root.getByRole("textbox", { name: "Manufacturer", exact: true }).fill("e2e-manufacturer");
    await instrumentForm.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await expect(pageInventory.detailsPanel.heading).toContainText(instrumentName);
    await expect(pageInventory.detailsPanel.root.getByRole("group", { name: "Instrument Template" })).toContainText(
      DEFAULT_TEMPLATE_NAME,
    );
  });
});
