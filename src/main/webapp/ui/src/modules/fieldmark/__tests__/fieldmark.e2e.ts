import { storageStatePath } from "@/__tests__/e2e/authState";
import { expect, tags, test } from "@/__tests__/e2e/fixtures";
import { INTEGRATION_MODE } from "@/__tests__/e2e/integrationMode";
import type { FieldmarkDialogComponent } from "./pageObjects/FieldmarkDialogComponent";

/**
 * Notebook name must match the fixture in
 * src/modules/fieldmark/__tests__/fieldmarkMock/fixtures/notebooks.json → metadata.name.
 * In real mode the live Fieldmark API must return the same notebook (drift check).
 */
const EXPECTED = {
  notebookName: "RSpace IGSN Demo",
  containerNamePrefix: "Container RSpace IGSN Demo",
};

/**
 * In mock mode any non-empty string is accepted by the mock server's
 * POST /auth/exchange-long-lived-token handler.
 */
const MOCK_API_KEY = "mock-fieldmark-token";

test.describe
  .serial(`Fieldmark integration [${INTEGRATION_MODE}] ${tags.APPS}`, () => {
    // Enable fieldmark.available at the system level (sysadmin).
    test.beforeAll(async ({ flowSysadminConfig }) => {
      await flowSysadminConfig.ensureSetting("fieldmark.available", "ALLOWED");
    });

    // Enable Fieldmark for the test user via the Apps page. Uses the
    // appUser's storageState rather than logging in again — a second
    // concurrent login as the same user racing the project's other specs'
    // `flowLogin` can trip Hibernate's optimistic lock on `User`.
    test.beforeAll(async ({ browser, browserContextOptions, appUser }) => {
      const ctx = await browser.newContext({
        ...browserContextOptions,
        storageState: storageStatePath(appUser.username),
      });
      try {
        const page = await ctx.newPage();
        await page.goto("/apps");
        await page.locator('div[aria-label="Fieldmark"]').click();
        const dialog = page.getByRole("dialog");
        await dialog.getByRole("textbox", { name: "API Key" }).fill(MOCK_API_KEY);
        await dialog.getByRole("button", { name: "Save" }).click();
        const enableBtn = dialog.getByRole("button", { name: "ENABLE" });
        if (await enableBtn.isVisible().catch(() => false)) {
          await enableBtn.click();
        } else {
          await dialog.getByRole("button", { name: "Close" }).click();
        }
        await dialog.waitFor({ state: "detached" });
      } finally {
        await ctx.close();
      }
    });

    test("Fieldmark enabled — Create menu shows 'Fieldmark' under Third-Party Import", async ({
      flowLogin,
      pageInventory,
      page,
    }) => {
      void flowLogin;

      await test.step("Given I am on the Inventory page", async () => {
        await pageInventory.open();
        await pageInventory.isLoaded();
      });

      await test.step("When I open the Create menu", async () => {
        await pageInventory.createButton.click();
      });

      await test.step("Then a 'Fieldmark' menu item is visible", async () => {
        await expect(page.getByRole("menuitem", { name: "Fieldmark" })).toBeVisible();
        await page.keyboard.press("Escape");
      });
    });

    test("Opening the Fieldmark dialog lists available notebooks", async ({ flowLogin, pageInventory }) => {
      void flowLogin;
      let dialog!: FieldmarkDialogComponent;

      await test.step("Given I have opened the Fieldmark import dialog", async () => {
        await pageInventory.open();
        await pageInventory.isLoaded();
        dialog = await pageInventory.openFieldmarkImport();
      });

      await test.step(`Then the notebook '${EXPECTED.notebookName}' appears in the grid`, async () => {
        await expect(dialog.notebookRadio(EXPECTED.notebookName)).toBeVisible();
      });

      await test.step("And closing the dialog dismisses it", async () => {
        await dialog.closeButton.click();
        await dialog.root.waitFor({ state: "detached" });
      });
    });

    test("Importing a notebook creates a container and shows a success toast", async ({
      flowLogin,
      pageInventory,
      page,
    }) => {
      void flowLogin;
      let dialog!: FieldmarkDialogComponent;
      let containerName!: string;

      await test.step(`Given I have selected the notebook '${EXPECTED.notebookName}'`, async () => {
        await pageInventory.open();
        await pageInventory.isLoaded();
        dialog = await pageInventory.openFieldmarkImport();
        await dialog.selectNotebook(EXPECTED.notebookName);
      });

      await test.step("When I click Import", async () => {
        const [response] = await Promise.all([
          page.waitForResponse((r) => r.url().includes("/api/inventory/v1/import/fieldmark/notebook")),
          dialog.importButton.click(),
        ]);
        if (!response.ok()) {
          throw new Error(
            `POST /api/inventory/v1/import/fieldmark/notebook failed: ${response.status()} ${response.statusText()}`,
          );
        }
        const body = (await response.json()) as {
          containerName: string;
          containerGlobalId: string;
        };
        containerName = body.containerName;
      });

      await test.step("Then a success toast confirms the import", async () => {
        await expect(
          page
            .locator('[data-testid="Toasts"]')
            .getByRole("group", { name: "success alert" })
            .filter({ hasText: "Successfully imported notebook." }),
        ).toBeVisible({ timeout: 5_000 });
        await dialog.root.waitFor({ state: "detached" });
      });

      await test.step(`And the container name starts with '${EXPECTED.containerNamePrefix}'`, async () => {
        expect(containerName).toMatch(new RegExp(`^${EXPECTED.containerNamePrefix}`));
      });
    });
  });
