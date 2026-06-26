import { expect, test } from "@/__tests__/e2e/fixtures";
import { INTEGRATION_MODE } from "@/__tests__/e2e/integrationMode";
import { AppShell } from "@/__tests__/e2e/pageObjects/AppShell";
import { AppsPage } from "@/__tests__/e2e/pageObjects/AppsPage";
import { PubchemDialogPage } from "./pageObjects/PubchemDialogPage";

// Mocking is now backend-side: in mock mode the app's pubchem.base.url points at
// the fake PubChem server (MSW handlers + Hono), so the real connector returns
// this shape. In real mode the live API does — same assertion is the drift check.
const ASPIRIN_DTO = {
  name: "Aspirin",
  smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
  formula: "C9H8O4",
  pubchemId: "2244",
  cas: "50-78-2",
};

test.describe
  .serial(`PubChem import [${INTEGRATION_MODE}]`, () => {
    test("inaccessible while the integration is disabled", async ({ page, appUser }) => {
      await new AppShell(page).login(appUser);
      await new AppsPage(page).setEnabled("Chemistry", false);
      await new AppShell(page).openBasicDocument();
      const dialog = new PubchemDialogPage(page);
      await expect(dialog.toolbarButton).toHaveCount(0);
      await expect(dialog.root).toHaveCount(0);
    });

    test("enabled: search 'aspirin' returns the compound DTO", async ({ page, appUser }) => {
      const shell = new AppShell(page);
      const dialog = new PubchemDialogPage(page);
      await shell.login(appUser);
      await new AppsPage(page).setEnabled("Chemistry", true);
      await shell.openBasicDocument();

      await expect(dialog.toolbarButton).toBeVisible();
      await dialog.open();

      const [resp] = await Promise.all([
        page.waitForResponse((r) => r.url().endsWith("/api/v1/pubchem/search") && r.status() === 200),
        dialog.search("aspirin"),
      ]);

      const body = (await resp.json()) as Array<Record<string, string>>;
      const aspirin = body.find((c) => c.name === "Aspirin");
      expect(aspirin).toMatchObject({
        name: ASPIRIN_DTO.name,
        smiles: ASPIRIN_DTO.smiles,
        formula: ASPIRIN_DTO.formula,
        pubchemId: ASPIRIN_DTO.pubchemId,
        cas: ASPIRIN_DTO.cas,
      });
      await expect(dialog.result("Aspirin")).toBeVisible();
    });

    test("disable, then re-run: inaccessible again", async ({ page, appUser }) => {
      await new AppShell(page).login(appUser);
      await new AppsPage(page).setEnabled("Chemistry", false);
      await new AppShell(page).openBasicDocument();
      const dialog = new PubchemDialogPage(page);
      await expect(dialog.toolbarButton).toHaveCount(0);
      await expect(dialog.root).toHaveCount(0);
    });
  });
