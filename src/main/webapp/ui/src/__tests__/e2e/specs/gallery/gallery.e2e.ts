import { expect, type Frame } from "@playwright/test";
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

  /*
   * The Create button opens a menu; it must never navigate. A stray `href`, or
   * a default `type="submit"` inside one of the surrounding legacy JSP forms,
   * would submit/reload the page and destroy the menu (and any unsaved state)
   * instead. `framenavigated` covers both a full document load and a
   * same-document SPA route change, so this catches either regression.
   */
  test("As a user, clicking Create opens the menu without navigating", async ({ pageGallery, page }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    const urlBefore = page.url();
    const navigations: string[] = [];
    const recordNavigation = (frame: Frame) => {
      if (frame === page.mainFrame()) navigations.push(frame.url());
    };
    page.on("framenavigated", recordNavigation);

    try {
      await pageGallery.openCreateMenu();

      await expect(page.getByRole("menu")).toBeVisible();
      expect(navigations).toEqual([]);
      expect(page.url()).toBe(urlBefore);
    } finally {
      page.off("framenavigated", recordNavigation);
    }
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
