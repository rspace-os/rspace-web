import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const EXPECTED = {
  notebookName: "RSpace IGSN Demo",
  containerNamePrefix: "Container RSpace IGSN Demo",
};

const INTEGRATION_MODE = env.integrationMode;
const FIELDMARK_API_KEY = "mock-fieldmark-token";

test.describe(`Fieldmark integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode is broken");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("fieldmark.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("Fieldmark", FIELDMARK_API_KEY);
  });

  test("As a user, I can import a notebook as a container and see confirmation", async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    await pageInventory.open();
    await pageInventory.isLoaded();
    const dialog = await pageInventory.openFieldmarkImport();
    await dialog.selectNotebook(EXPECTED.notebookName);

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.request().method() === "POST" &&
          new URL(r.url()).pathname.endsWith("/api/inventory/v1/import/fieldmark/notebook"),
      ),
      dialog.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(
        `POST /api/inventory/v1/import/fieldmark/notebook failed: ${response.status()} ${response.statusText()}`,
      );
    }
    const body = (await response.json()) as {
      containerName: string;
    };
    const containerName = body.containerName;

    await expect(componentToasts.byVariant("success", "Successfully imported notebook.")).toBeVisible();
    await dialog.root.waitFor({ state: "detached" });

    expect(containerName.startsWith(EXPECTED.containerNamePrefix)).toBe(true);
  });
});
