import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Aspose (.DOC export)", { tag: tags.APPS }, () => {
  test("As a user, the .DOC export option is absent when aspose.enabled is false", async ({
    page,
    clientDocuments,
    pageDocument,
    componentExportWizard,
  }) => {
    const doc = await clientDocuments.create({ name: uniqueName("aspose disabled export") });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();

    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();

    await expect(componentExportWizard.root.getByRole("radio", { name: ".DOC file" })).toHaveCount(0);
  });

  test("As a user, the .DOC export option appears and is selectable when aspose.enabled is true", async ({
    page,
    clientDocuments,
    pageDocument,
    componentExportWizard,
  }) => {
    await page.route("**/deploymentproperties/ajax/property*", async (route) => {
      const url = new URL(route.request().url());
      if (url.searchParams.get("name") === "aspose.enabled") {
        await route.fulfill({ json: true });
        return;
      }
      await route.continue();
    });

    const doc = await clientDocuments.create({ name: uniqueName("aspose enabled export") });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();

    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();

    const docRadio = componentExportWizard.root.getByRole("radio", { name: ".DOC file" });
    await expect(docRadio).toBeVisible();
    await expect(docRadio).toBeEnabled();

    await componentExportWizard.selectFormat("doc");
    await expect(docRadio).toBeChecked();
  });
});
