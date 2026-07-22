import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";
import { MOBILE_DEVICE } from "@/__tests__/e2e/viewports";

const TINY_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);

test.describe("Gallery", () => {
  test("As a user, I can navigate to the Images section and browse the Examples folder", async ({ pageGallery }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    await pageGallery.openSection("Images");
    await pageGallery.openFolder("Examples");

    await pageGallery.waitForFile("anaphase.jpg");
  });

  test.describe("mobile", () => {
    test.use(MOBILE_DEVICE);

    test("As a user, I can navigate to the Images section on a mobile viewport", async ({ pageGallery }) => {
      await pageGallery.open();
      await pageGallery.isLoaded();

      await pageGallery.openSection("Images");
      await pageGallery.openFolder("Examples");

      await pageGallery.waitForFile("anaphase.jpg");
    });
  });

  test("As a user, I can see a selected file's Global ID in the info panel", async ({ clientFiles, pageGallery }) => {
    const fileName = `${uniqueName("e2e-gallery-global-id")}.png`;
    const uploaded = await clientFiles.uploadFile({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });

    await pageGallery.openFile(uploaded.id);
    await pageGallery.isLoaded();
    await pageGallery.infoPanel.waitUntilSelected(fileName);

    await expect.poll(() => pageGallery.infoPanel.detail("Global ID")).toBe(uploaded.globalId);
  });

  test("As a user, I can switch to Tree view", async ({ pageGallery, page }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    await pageGallery.views.switchTo("Tree");

    await expect(page.getByRole("tree")).toBeVisible();
  });

  test("As a user, I can sort files by Name", async ({ pageGallery }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();
    await pageGallery.openSection("Images");
    await pageGallery.openFolder("Examples");
    await pageGallery.waitForFile("anaphase.jpg");

    await pageGallery.sort.sortBy("Name");

    await expect(pageGallery.fileGrid.getByRole("gridcell").first()).toHaveText("anaphase.jpg");
  });
});
