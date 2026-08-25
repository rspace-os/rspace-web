import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Instrument`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can create an Instrument from an Instrument Template`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const templateName = uniqueName("e2e-instrument-template");

    await pageInventory.open();
    const templateMenu = await pageInventory.openCreateMenu();
    const templateForm = await templateMenu.newInstrumentTemplate();
    await templateForm.fillName(templateName);
    await templateForm.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await componentToasts.dismissAll();

    const dialog = await pageInventory.detailsPanel.openCreateItemDialog();
    const instrumentForm = await dialog.chooseInstrument();
    await instrumentForm.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await expect(pageInventory.detailsPanel.heading).toContainText(templateName);
    await expect(pageInventory.detailsPanel.root.getByRole("group", { name: "Instrument Template" })).toContainText(
      templateName,
    );
  });

  test(`As a user, adding a custom field to a Template after creating an Instrument from it does not affect the existing Instrument`, async ({
    pageInventory,
    componentToasts,
    clientInventory,
    page,
  }) => {
    const templateName = uniqueName("e2e-instrument-template-gap");
    const instrumentName = uniqueName("e2e-instrument-from-gap-template");

    const template = await clientInventory.createInstrumentTemplate({ name: templateName });
    await clientInventory.createInstrument({ name: instrumentName, templateId: template.id });

    await test.step("When a custom field is added to the template after the instrument already exists", async () => {
      await pageInventory.openRecord("INSTRUMENT_TEMPLATE", templateName);

      const customFields = pageInventory.detailsPanel.section("Custom Fields");
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      await customFields.getByRole("button", { name: "Add new field", exact: true }).click();

      await page.getByTestId("TemplateField").getByRole("textbox").fill("Serial Number");
      await pageInventory.detailsPanel.saveEdit();
      await componentToasts.dismissAll();
    });

    await pageInventory.openRecord("INSTRUMENT", instrumentName);
    await pageInventory.detailsPanel.expandSection("Custom Fields");

    await expect(pageInventory.detailsPanel.section("Custom Fields").getByText("Serial Number")).toHaveCount(0);
  });

  test(`As a user, a plain Instrument with no template supports its own custom field and can be duplicated`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const instrumentName = uniqueName("e2e-plain-instrument");

    await pageInventory.open();
    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newInstrument();
    await form.fillName(instrumentName);
    await form.expandSection("Custom Fields");
    await form.customFields().addNewTextField("Serial Number");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await expect(pageInventory.detailsPanel.root.getByRole("group", { name: "Instrument Template" })).toContainText(
      "No Template",
    );

    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await expect(pageInventory.detailsPanel.section("Custom Fields").getByText("Serial Number")).toBeVisible();

    await test.step("And duplicating it carries the custom field over", async () => {
      await (await pageInventory.detailsPanel.duplicateControl()).click();
      await expect(pageInventory.detailsPanel.heading).toContainText(`${instrumentName}_COPY`);
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      await expect(pageInventory.detailsPanel.section("Custom Fields").getByText("Serial Number")).toBeVisible();
    });
  });

  test(`As a user, I can create an Instrument Template from an existing Instrument`, async ({
    pageInventory,
    clientInventory,
  }) => {
    const instrumentName = uniqueName("e2e-instrument-for-template");
    const templateName = uniqueName("e2e-template-from-instrument");

    const instrument = await clientInventory.createInstrument({ name: instrumentName });

    await pageInventory.openInstrument(instrument.id);
    const dialog = await pageInventory.detailsPanel.openCreateItemDialog();
    await dialog.createInstrumentTemplate(templateName);

    await pageInventory.openSearch("INSTRUMENT_TEMPLATE");
    await pageInventory.searchPanel.search(templateName);
    await expect(pageInventory.searchPanel.row(templateName)).toBeVisible();
  });
});
