import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Sample Template creation`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can create a template with every custom field type`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    const templateName = uniqueName("e2e-template");

    await pageInventory.open();
    await pageInventory.isLoaded();

    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Text", "Plain Text Field");
    await form.addCustomField("Number", "Number Field");
    await form.addCustomField("Choice", "Choice Field", "Option A");
    await form.addCustomField("Radio", "Radio Field", "Option A");
    await form.addCustomField("Date", "Date Field");
    await form.addCustomField("Time", "Time Field");
    await form.addCustomField("FormattedText", "Formatted Text Field");
    await form.addCustomField("Uri", "URI Field");
    await form.addCustomField("Attachment", "Attachment Field");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await componentToasts.dismissAll();

    await pageInventory.detailsPanel.expandSection("Custom Fields");
    for (const fieldName of [
      "Plain Text Field",
      "Number Field",
      "Choice Field",
      "Radio Field",
      "Date Field",
      "Time Field",
      "Formatted Text Field",
      "URI Field",
      "Attachment Field",
    ]) {
      await expect(pageInventory.detailsPanel.section(fieldName)).toBeVisible();
    }

    // SPA navigation leaves the mobile details panel covering the search results.
    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(templateName);
    await expect(pageInventory.searchPanel.row(templateName)).toBeVisible();
  });
});
