import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const currentDir = dirname(fileURLToPath(import.meta.url));
const DNA_FILE = resolve(currentDir, "fixtures/alpha-2-macroglobulin.gb");
const DNA_FILE_NAME = "alpha-2-macroglobulin.gb";

const MOCK_ENZYME_NAME = "EcoRI";
const MOCK_ORF_TRANSLATION = "MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKR";

const INTEGRATION_MODE = env.integrationMode;

test.describe("SnapGene integration [mock]", { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "no real SnapGene server reachable from this suite — mock-only");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("snapgene.available", "ALLOWED");
  });

  test("As a user, I can preview a DNA file's map, enzyme sites, FASTA sequence and ORFs via SnapGene", async ({
    page,
    pageWorkspace,
    pageGallery,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();

    const picker = await docEditor.openGalleryPicker();
    await picker.goToSection("Miscellaneous");
    await picker.uploadFile(DNA_FILE, DNA_FILE_NAME);
    await picker.cancelButton.click();

    await pageGallery.open();
    await pageGallery.openSection("Miscellaneous");
    await pageGallery.selectFile(DNA_FILE_NAME);
    await pageGallery.actions.open();
    await pageGallery.actions.clickAction("View");

    await expect(page.getByRole("heading", { name: "SnapGene" })).toBeVisible();

    // DNA preview tab (open by default) — a real PNG from the mock service.
    const dnaPreviewImage = page.getByRole("img", { name: "DNA preview" });
    await expect(dnaPreviewImage).toBeVisible();
    const imageSrc = await dnaPreviewImage.getAttribute("src");
    if (!imageSrc) {
      throw new Error("DNA preview image has no src attribute");
    }
    const imageResponse = await page.request.get(imageSrc);
    expect(imageResponse.ok()).toBe(true);
    expect((await imageResponse.body()).length).toBeGreaterThan(0);

    await page.getByRole("tab", { name: "Enzyme sites" }).click();
    await expect(page.getByRole("cell", { name: MOCK_ENZYME_NAME })).toBeVisible();

    await page.getByRole("tab", { name: "View as FASTA" }).click();
    await expect(page.getByText("MockSeq")).toBeVisible();

    await page.getByRole("tab", { name: "ORF table" }).click();
    await expect(page.getByRole("cell", { name: MOCK_ORF_TRANSLATION })).toBeVisible();
  });
});
