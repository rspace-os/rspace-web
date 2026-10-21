import { expect, type Page } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function mockConversionEnabled(page: Page, enabled: boolean): Promise<void> {
  await page.route("**/deploymentproperties/ajax/property*", async (route) => {
    const url = new URL(route.request().url());
    if (url.searchParams.get("name") === "conversion.enabled") {
      await route.fulfill({ json: enabled });
      return;
    }
    await route.continue();
  });
}

test.describe("Word (.DOCX) export", { tag: tags.APPS }, () => {
  test("As a user, the .DOCX export option is absent when conversion.enabled is false", async ({
    page,
    clientDocuments,
    pageDocument,
    componentExportWizard,
  }) => {
    await mockConversionEnabled(page, false);
    const doc = await clientDocuments.create({ name: uniqueName("conversion disabled export") });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();

    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();

    // The format list has rendered, so the missing .DOCX option is meaningful.
    await expect(componentExportWizard.root.getByRole("radio", { name: "PDF file" })).toBeVisible();
    await expect(componentExportWizard.root.getByRole("radio", { name: ".DOCX file" })).toHaveCount(0);
  });

  test("As a user, the .DOCX export option appears and is selectable when conversion.enabled is true", async ({
    page,
    clientDocuments,
    pageDocument,
    componentExportWizard,
  }) => {
    await mockConversionEnabled(page, true);
    const doc = await clientDocuments.create({ name: uniqueName("conversion enabled export") });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();

    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();

    const docRadio = componentExportWizard.root.getByRole("radio", { name: ".DOCX file" });
    await expect(docRadio).toBeVisible();
    await expect(docRadio).toBeEnabled();

    await componentExportWizard.selectFormat("doc");
    await expect(docRadio).toBeChecked();
  });
});
