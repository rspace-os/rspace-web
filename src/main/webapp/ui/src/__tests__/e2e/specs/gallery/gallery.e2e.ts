import { expect, type Frame } from "@playwright/test";
import type { FilesClient } from "@/__tests__/e2e/api/clients/FilesClient";
import type { FoldersClient } from "@/__tests__/e2e/api/clients/FoldersClient";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

/**
 * Creates an Images subfolder holding the given PNGs, so tests do not rely on seeded examples.
 * Callers use a fresh dynamic user so the folder is on the first page of the Images listing.
 */
async function createImagesFolder(
  clientFiles: FilesClient,
  clientFolders: FoldersClient,
  fileNames: string[],
): Promise<string> {
  // An upload without a folder lands in Images > Api Inbox, whose parent is the Images folder.
  const inboxUpload = await clientFiles.uploadFile({
    name: `${uniqueName("e2e-gallery-inbox")}.png`,
    mimeType: "image/png",
    buffer: TINY_PNG,
  });
  if (inboxUpload.parentFolderId === undefined) throw new Error("Uploaded file has no parentFolderId");
  const inbox = await clientFolders.get(inboxUpload.parentFolderId);
  if (inbox.parentFolderId === undefined) throw new Error("Api Inbox has no parentFolderId");
  const folder = await clientFolders.create({
    name: uniqueName("e2e-gallery-folder"),
    parentFolderId: inbox.parentFolderId,
  });
  for (const name of fileNames) {
    await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG, folderId: folder.id });
  }
  return folder.name;
}

test.describe("Gallery", { tag: tags.MOBILE }, () => {
  dynamicUserTest(
    "As a user, I can navigate to the Images section and browse a folder",
    async ({ clientFiles, clientFolders, pageGallery }) => {
      const fileName = `${uniqueName("e2e-gallery-browse")}.png`;
      const folderName = await createImagesFolder(clientFiles, clientFolders, [fileName]);

      await pageGallery.openInSection("Images");
      await pageGallery.openFolder(folderName);

      await pageGallery.waitForFile(fileName);
    },
  );

  dynamicUserTest(
    "As a user, the first click on a file selects it",
    async ({ clientFiles, clientFolders, page, pageGallery }) => {
      const fileName = `${uniqueName("e2e-gallery-select")}.png`;
      const folderName = await createImagesFolder(clientFiles, clientFolders, [fileName]);

      await pageGallery.openInSection("Images");
      await pageGallery.openFolder(folderName);
      await pageGallery.waitForFile(fileName);
      const urlBeforeClick = page.url();
      const file = pageGallery.fileCell(fileName);
      await file.click();
      await expect(file).toHaveAttribute("aria-selected", "true");
      await pageGallery.infoPanel.waitUntilSelected(fileName);
      await expect(page).toHaveURL(urlBeforeClick);
    },
  );

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

  dynamicUserTest("As a user, I can sort files by Name", async ({ clientFiles, clientFolders, pageGallery }) => {
    const base = uniqueName("e2e-gallery-sort");
    const [first, second] = [`${base}-a.png`, `${base}-b.png`];
    const folderName = await createImagesFolder(clientFiles, clientFolders, [first, second]);

    await pageGallery.openInSection("Images");
    await pageGallery.openFolder(folderName);
    await pageGallery.waitForFile(second);

    await pageGallery.sort.sortBy("Name");

    await expect(pageGallery.fileGrid.getByRole("gridcell").first()).toHaveText(first);
  });
});
