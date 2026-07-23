import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import type { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const FIELD_NAME = "Related Item";
const MANDATORY_ERROR = "This field is mandatory. Please enter a value.";

async function propagateTemplateChangeToSamples(page: Page, toasts: ToastsComponent): Promise<void> {
  const toast = toasts.byVariant("notice", "Update existing samples?");
  await expect(toast).toBeVisible();
  await toasts.clickAction(toast, "yes");
  await page
    .getByRole("dialog", { name: "Update all samples to latest template version" })
    .getByRole("button", { name: "Update all", exact: true })
    .click();
  await expect(toasts.byVariant("success", "successfully updated")).toBeVisible();
}

test.describe(`Sample Template Link fields`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can define a Link field on a Sample Template with Mandatory and an allowed-relationship-type whitelist`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const templateName = uniqueName("e2e-linktemplate");

    await pageInventory.open();
    await pageInventory.isLoaded();

    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Link", FIELD_NAME);
    await form.setFieldMandatory(FIELD_NAME);
    await form.addAllowedRelationType(FIELD_NAME, "IsPartOf");
    await form.addAllowedRelationType(FIELD_NAME, "References");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    const fieldRegion = pageInventory.detailsPanel.section(FIELD_NAME);
    await expect(fieldRegion).toBeVisible();
    await expect(fieldRegion).toContainText("Mandatory");
  });

  test(`As a user, I can see Sample creation blocked when its Template's Mandatory Link field is empty`, async ({
    pageInventory,
    componentToasts,
  }) => {
    const templateName = uniqueName("e2e-linktemplate-mandatory");
    const sampleName = uniqueName("e2e-linktemplate-mandatory-sample");

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu1 = await pageInventory.openCreateMenu();
    const form = await menu1.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Link", FIELD_NAME);
    await form.setFieldMandatory(FIELD_NAME);
    await form.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const menu2 = await pageInventory.openCreateMenu();
    const sample = await menu2.newSample();
    await sample.fillName(sampleName);
    await sample.selectTemplate(templateName);
    await sample.expandSection("Custom Fields");

    await expect(sample.saveButton).toBeDisabled();
    await expect(sample.root.getByRole("group", { name: FIELD_NAME }).getByText(MANDATORY_ERROR)).toBeVisible();
  });

  test(`As a user, I can propagate a Template Link field whitelist change to existing Samples`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    test.setTimeout(120_000);
    const templateName = uniqueName("e2e-linktemplate-propagate");
    const sampleName = uniqueName("e2e-linktemplate-propagate-sample");

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Link", FIELD_NAME);
    await form.addAllowedRelationType(FIELD_NAME, "IsPartOf");
    await form.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const menu2 = await pageInventory.openCreateMenu();
    const sample = await menu2.newSample();
    await sample.fillName(sampleName);
    await sample.selectTemplate(templateName);
    await sample.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(templateName);
    await pageInventory.searchPanel.open(templateName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    const customFields = pageInventory.detailsPanel.customFields();
    await customFields.addAllowedRelationType(FIELD_NAME, "References");
    await customFields.setFieldMandatory(FIELD_NAME);
    await pageInventory.detailsPanel.saveEdit();
    await propagateTemplateChangeToSamples(page, componentToasts);

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.open(sampleName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    const saveButton = pageInventory.detailsPanel.root.getByRole("button", { name: "Save", exact: true });
    await expect(saveButton).toBeDisabled();
    await expect(
      pageInventory.detailsPanel.root.getByRole("group", { name: FIELD_NAME }).getByText(MANDATORY_ERROR),
    ).toBeVisible();
  });

  test(`As a user, I can propagate a Template Link field content change to every existing Sample`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    test.setTimeout(120_000);
    const templateName = uniqueName("e2e-linktemplate-multi");
    const sampleNames = [0, 1, 2].map((i) => uniqueName(`e2e-linktemplate-multi-sample-${i}`));

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Link", FIELD_NAME);
    await form.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    for (const name of sampleNames) {
      const sampleMenu = await pageInventory.openCreateMenu();
      const sample = await sampleMenu.newSample();
      await sample.fillName(name);
      await sample.selectTemplate(templateName);
      await sample.save();
      await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();
    }

    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(templateName);
    await pageInventory.searchPanel.open(templateName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await pageInventory.detailsPanel.customFields().setFieldMandatory(FIELD_NAME);
    await pageInventory.detailsPanel.saveEdit();
    await propagateTemplateChangeToSamples(page, componentToasts);

    for (const name of sampleNames) {
      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.isLoaded();
      await pageInventory.searchPanel.search(name);
      await pageInventory.searchPanel.open(name);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.expandSection("Custom Fields");
      const saveButton = pageInventory.detailsPanel.root.getByRole("button", { name: "Save", exact: true });
      await expect(saveButton).toBeDisabled();
      await expect(
        pageInventory.detailsPanel.root.getByRole("group", { name: FIELD_NAME }).getByText(MANDATORY_ERROR),
      ).toBeVisible();
    }
  });

  test(`As a user, I can save a propagated Mandatory Link field when it has a valid link but not when it is empty`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    page,
  }) => {
    test.setTimeout(120_000);
    const templateName = uniqueName("e2e-linktemplate-mixed");
    const filledSampleName = uniqueName("e2e-linktemplate-mixed-filled");
    const emptySampleName = uniqueName("e2e-linktemplate-mixed-empty");

    const linkTarget = await test.step("Given a Sample exists to link to", async () => {
      return clientInventory.createSample({ name: uniqueName("e2e-linktemplate-mixed-target") });
    });

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSampleTemplate();
    await form.fillName(templateName);
    await form.expandSection("Custom Fields");
    await form.addCustomField("Link", FIELD_NAME);
    await form.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const filledMenu = await pageInventory.openCreateMenu();
    const filledSample = await filledMenu.newSample();
    await filledSample.fillName(filledSampleName);
    await filledSample.selectTemplate(templateName);
    await filledSample.expandSection("Custom Fields");

    const linkEditor = filledSample.customFields().linkFieldEditor();
    await linkEditor.setRelationType("References");
    await linkEditor.setTargetGlobalId(linkTarget.globalId);
    await linkEditor.apply();
    await filledSample.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    const emptyMenu = await pageInventory.openCreateMenu();
    const emptySample = await emptyMenu.newSample();
    await emptySample.fillName(emptySampleName);
    await emptySample.selectTemplate(templateName);
    await emptySample.save();
    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await page.goto("/inventory/search?resultType=SAMPLE_TEMPLATE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(templateName);
    await pageInventory.searchPanel.open(templateName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await pageInventory.detailsPanel.customFields().setFieldMandatory(FIELD_NAME);
    await pageInventory.detailsPanel.saveEdit();
    await propagateTemplateChangeToSamples(page, componentToasts);

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(emptySampleName);
    await pageInventory.searchPanel.open(emptySampleName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await expect(
      pageInventory.detailsPanel.root.getByRole("group", { name: FIELD_NAME }).getByText(MANDATORY_ERROR),
    ).toBeVisible();

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();
    await pageInventory.searchPanel.search(filledSampleName);
    await pageInventory.searchPanel.open(filledSampleName);
    await pageInventory.detailsPanel.enterEditMode();
    await pageInventory.detailsPanel.expandSection("Custom Fields");
    await pageInventory.detailsPanel.saveEdit();
    await expect(componentToasts.byVariant("success", "successfully")).toBeVisible();
  });
});
