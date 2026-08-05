import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const EXPECTED = {
  name: "Aspirin",
  formula: "C9H8O4",
  cas: "50-78-2",
};
const INTEGRATION_MODE = env.integrationMode;

test.describe(`PubChem integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, I can search PubChem by compound name", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openPubchemDialog();

    await dialog.search("aspirin");

    await expect(dialog.resultCard(EXPECTED.name)).toBeVisible();
    await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.formula)).toBeVisible();
    await expect(dialog.resultCardField(EXPECTED.name, EXPECTED.cas)).toBeVisible();
  });

  test("As a user, I can import a PubChem compound into a document", async ({ page, pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openPubchemDialog();
    await dialog.search("aspirin");

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/chemical/ajax/createChemElement"),
      ),
      dialog.importCompound(EXPECTED.name),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /chemical/ajax/createChemElement failed: ${response.status()} ${response.statusText()}`);
    }

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemElement).toBeVisible();
  });
});
