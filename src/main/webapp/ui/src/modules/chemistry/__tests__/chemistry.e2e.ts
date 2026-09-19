import { readFileSync } from "node:fs";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

const ASPIRIN_SMILES = "CC(=O)Oc1ccccc1C(=O)O";
const ASPIRIN = {
  name: "Aspirin",
  formula: "C9H8O4",
};

const CHEMDRAW_CDX = fixturePath(import.meta.url, "fixtures/Fluorescein1.cdx");

test.describe(`Chemistry service [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, converting a SMILES structure returns a real MOL block from the chemistry service", async ({
    page,
  }) => {
    const response = await page.request.post("/chemical/ajax/convert", {
      data: { structure: ASPIRIN_SMILES, inputFormat: "smiles" },
    });
    expect(response.ok()).toBe(true);

    const converted = await response.json();
    expect(converted.errorMessage).toBeFalsy();
    expect(converted.structure).toContain("M  END");
  });

  test("As a user, importing a compound renders a real chemistry-service image, not an empty placeholder", async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openPubchemDialog();
    await dialog.search("aspirin");

    const [createResponse] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/chemical/ajax/createChemElement"),
      ),
      dialog.importCompound(ASPIRIN.name),
    ]);
    if (!createResponse.ok()) {
      throw new Error(
        `POST /chemical/ajax/createChemElement failed: ${createResponse.status()} ${createResponse.statusText()}`,
      );
    }

    const field = await docEditor.getField("New List of Materials");
    await expect(field.chemElement).toBeVisible();

    const imageSrc = await field.chemElement.getAttribute("src");
    if (!imageSrc) {
      throw new Error("Chem element image has no src attribute");
    }

    const imageResponse = await page.request.get(imageSrc);
    expect(imageResponse.ok()).toBe(true);
    const bytes = await imageResponse.body();
    expect(bytes.length).toBeGreaterThan(100);
  });

  test("As a user, uploading a ChemDraw (.cdx) file to the Gallery is converted into a real chemical structure", async ({
    page,
  }) => {
    const response = await page.request.post("/gallery/ajax/uploadFile", {
      multipart: {
        xfile: {
          name: "Fluorescein1.cdx",
          mimeType: "application/octet-stream",
          buffer: readFileSync(CHEMDRAW_CDX),
        },
      },
    });
    expect(response.ok()).toBe(true);

    const { data, error } = await response.json();
    expect(error).toBeFalsy();
    expect(data.name).toBe("Fluorescein1.cdx");
    expect(data.chemString).toBeTruthy();
  });
});
