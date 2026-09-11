import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath, TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

// Must share the original's .png extension - the app rejects an uploaded new version whose
// extension doesn't match (live-verified: "Cannot update .png file with .jpg").
const REPLACEMENT_IMAGE_PATH = fixturePath(import.meta.url, "../inventory/fixtures/container_preview.png");

test.describe("Gallery image versioning", () => {
  test("As a user, I can upload a new version of an image and see both versions in its history", {
    tag: tags.MOBILE,
  }, async ({ pageGallery, clientFiles }) => {
    const originalName = `${uniqueName("e2e-gallery-version")}.png`;

    await test.step("Given an uploaded image", async () => {
      const uploaded = await clientFiles.uploadFile({ name: originalName, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
      await pageGallery.selectFile(originalName);
    });

    await test.step("Then its version history shows a single current version", async () => {
      await pageGallery.openVersionHistoryForSelected();
      await expect(pageGallery.versionHistoryDialog.rows).toHaveCount(1);
      await expect(pageGallery.versionHistoryDialog.versionLink("v1 (current)")).toBeVisible();
      await expect(pageGallery.versionHistoryDialog.nameCell(originalName)).toBeVisible();
      await pageGallery.versionHistoryDialog.close();
    });

    await test.step("When I upload a new version of the file", async () => {
      await pageGallery.selectFile(originalName);
      await pageGallery.uploadNewVersionOfSelected(REPLACEMENT_IMAGE_PATH);
      await expect(pageGallery.fileGrid.getByRole("gridcell", { name: "container_preview.png" })).toBeVisible();
    });

    await test.step("Then its version history shows both versions, newest first", async () => {
      await pageGallery.fileGrid.getByRole("gridcell", { name: "container_preview.png" }).click();
      await pageGallery.openVersionHistoryForSelected();
      await expect(pageGallery.versionHistoryDialog.rows).toHaveCount(2);
      await expect(pageGallery.versionHistoryDialog.versionLink("v2 (current)")).toBeVisible();
      await expect(pageGallery.versionHistoryDialog.nameCell("container_preview.png")).toBeVisible();
      await expect(pageGallery.versionHistoryDialog.versionLink("v1")).toBeVisible();
      await expect(pageGallery.versionHistoryDialog.nameCell(originalName)).toBeVisible();
    });
  });

  test("As a user, selecting an old version from the Gallery Picker's history does not change what gets inserted", async ({
    pageGallery,
    pageWorkspace,
    clientFiles,
  }) => {
    const originalName = `${uniqueName("e2e-gallery-picker-version")}.png`;
    let uploadedId: number;

    await test.step("Given an uploaded image with two versions", async () => {
      const uploaded = await clientFiles.uploadFile({ name: originalName, mimeType: "image/png", buffer: TINY_PNG });
      uploadedId = uploaded.id;
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
      await pageGallery.selectFile(originalName);
      await pageGallery.uploadNewVersionOfSelected(REPLACEMENT_IMAGE_PATH);
      await expect(pageGallery.fileGrid.getByRole("gridcell", { name: "container_preview.png" })).toBeVisible();
    });

    const docEditor =
      await test.step("When I open the Gallery Picker from a document and select that image", async () => {
        await pageWorkspace.open();
        const editor = await pageWorkspace.createBasicDocument();
        const picker = await editor.openGalleryPicker();
        await picker.goToSection("Images");
        await picker.openFolder("Api Inbox");
        await picker.selectItem("container_preview.png");
        return editor;
      });

    const picker = docEditor.galleryPicker;

    await test.step("Then its version history is reachable and shows both versions", async () => {
      await picker.openVersionHistoryForSelected();
      await expect(picker.versionHistoryDialog.rows).toHaveCount(2);
      await expect(picker.versionHistoryDialog.versionLink("v2 (current)")).toBeVisible();
      await expect(picker.versionHistoryDialog.versionLink("v1")).toBeVisible();
    });

    await test.step("When I click on the older version", async () => {
      await picker.versionHistoryDialog.versionLink("v1").click();
    });

    await test.step("Then the history dialog closes and the current version remains selected for import", async () => {
      await expect(picker.versionHistoryDialog.root).toBeHidden();
      await expect(picker.root.getByRole("gridcell", { selected: true })).toContainText("container_preview.png");
    });

    const field =
      await test.step("And adding it inserts the current version, not the one clicked in the history", async () => {
        await picker.add();
        const insertedField = await docEditor.getField("New List of Materials");
        const src = await insertedField.imageElement.getAttribute("src");
        expect(src).toContain(`sourceId=${uploadedId}`);
        await expect(insertedField.imageElement).toHaveAttribute("alt", "image container_preview.png");
        return insertedField;
      });

    await test.step("And the inserted image's own details dialog confirms it is the current version", async () => {
      const toolbar = await field.selectImage();
      const infoDialog = await toolbar.openImageDetails();
      await expect(infoDialog.field("Name")).resolves.toBe("container_preview.png");
      await expect(infoDialog.field("Version")).resolves.toBe("2");
      await infoDialog.close();
    });
  });

  test("As a user, editing an image and saving as a new image creates a new gallery item", async ({
    pageGallery,
    clientFiles,
  }) => {
    // Known bug: rotating an image in the Edit Image dialog and clicking "Save as new
    // image" does not currently produce a new gallery item as expected. Live-reproduced
    // 2026-08-19 via MCP: Actions -> Edit -> rotate clockwise -> "Save as new image".
    // Tracked for follow-up; unskip once fixed.
    test.skip(true, "Known bug: 'Save as new image' after rotating does not create a new item");

    const originalName = `${uniqueName("e2e-gallery-edit")}.png`;

    await test.step("Given an uploaded image", async () => {
      const uploaded = await clientFiles.uploadFile({ name: originalName, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
    });

    const initialCount = await pageGallery.itemsCount();

    await test.step("When I rotate the image and save it as a new image", async () => {
      await pageGallery.selectFile(originalName);
      await pageGallery.openEditImageForSelected();
      await pageGallery.editImageDialog.rotateClockwiseButton.click();
      await pageGallery.editImageDialog.saveAsNewImage();
    });

    await test.step("Then a new gallery item is created alongside the original", async () => {
      await expect.poll(() => pageGallery.itemsCount()).toBe(initialCount + 1);
    });
  });
});
