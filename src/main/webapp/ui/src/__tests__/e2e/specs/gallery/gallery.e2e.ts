import { expect, type Frame } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Gallery", { tag: tags.MOBILE }, () => {
  test("As a user, I can navigate to the Images section and browse the Examples folder", async ({ pageGallery }) => {
    await pageGallery.openInSection("Images");
    await pageGallery.openFolder("Examples");

    await pageGallery.waitForFile("anaphase.jpg");
  });

  test("As a user, the first click on a file selects it", async ({ page, pageGallery }) => {
    await pageGallery.openInSection("Images");
    await pageGallery.openFolder("Examples");
    await pageGallery.waitForFile("anaphase.jpg");
    const urlBeforeClick = page.url();
    const file = pageGallery.fileCell("anaphase.jpg");
    await file.click();
    await expect(file).toHaveAttribute("aria-selected", "true");
    await pageGallery.infoPanel.waitUntilSelected("anaphase.jpg");
    await expect(page).toHaveURL(urlBeforeClick);
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
   * The Create button must open its menu without navigating. An `href` or implicit
   * `type="submit"` could reload a surrounding legacy JSP form and discard unsaved state.
   * `framenavigated` detects full-page loads and SPA route changes.
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
    await pageGallery.openInSection("Images");
    await pageGallery.openFolder("Examples");
    await pageGallery.waitForFile("anaphase.jpg");

    await pageGallery.sort.sortBy("Name");

    await expect(pageGallery.fileGrid.getByRole("gridcell").first()).toHaveText("anaphase.jpg");
  });
});
