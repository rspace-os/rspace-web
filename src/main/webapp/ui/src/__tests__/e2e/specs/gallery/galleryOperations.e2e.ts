import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Gallery folder and file operations", () => {
  test("As a user, I can create a folder, rename a file, move it in, and delete it", async ({
    pageGallery,
    clientFiles,
  }) => {
    const folderName = uniqueName("e2e-gallery-folder");
    const originalName = `${uniqueName("e2e-gallery-orig")}.png`;
    const renamedBaseName = uniqueName("e2e-gallery-renamed");
    const renamedName = `${renamedBaseName}.png`;

    await test.step("Given an uploaded file, with the new folder created as its sibling at the Images root", async () => {
      await clientFiles.uploadFile({ name: originalName, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.openInSection("Images");

      await pageGallery.createFolder(folderName);
      await pageGallery.openFolder("Api Inbox");
      await pageGallery.waitForFile(originalName);
    });

    await test.step("When I rename the uploaded file", async () => {
      await pageGallery.selectFile(originalName);
      await pageGallery.renameSelectedTo(renamedBaseName);
    });

    await test.step("Then the new name is shown and the old name is gone", async () => {
      await expect(pageGallery.fileCell(renamedName)).toBeVisible();
      await expect(pageGallery.fileCell(originalName)).toHaveCount(0);
    });

    await test.step("When I move the renamed file into the new folder", async () => {
      await pageGallery.fileCell(renamedName).click();
      await expect(pageGallery.fileCell(renamedName)).toHaveAttribute("aria-selected", "true");
      await pageGallery.moveSelectedTo(folderName);
    });

    await test.step("Then the file is gone from the parent folder", async () => {
      await expect(pageGallery.fileCell(renamedName)).toHaveCount(0);
    });

    await test.step("And the file is present inside the destination folder", async () => {
      await pageGallery.openInSection("Images");
      await pageGallery.openFolder(folderName);
      await expect(pageGallery.fileCell(renamedName)).toBeVisible();
    });

    await test.step("When I delete the file from inside the folder", async () => {
      await pageGallery.selectFile(renamedName);
      await pageGallery.actions.open();
      await pageGallery.actions.clickAction("Delete");
    });

    await test.step("Then it is gone", async () => {
      await expect(pageGallery.fileCell(renamedName)).toHaveCount(0);
    });
  });

  test("As a user, I can see the item count change when I upload and then delete a file", async ({
    pageGallery,
    clientFiles,
  }) => {
    const name = `${uniqueName("e2e-gallery-count")}.png`;

    await test.step("Given a fresh Images section with a known item count", async () => {
      const uploaded = await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
    });

    const initialCount = await pageGallery.itemsCount();

    await test.step("When I delete that file", async () => {
      await pageGallery.selectFile(name);
      await pageGallery.actions.open();
      await pageGallery.actions.clickAction("Delete");
      await expect(pageGallery.fileCell(name)).toHaveCount(0);
    });

    await test.step("Then the item count has decreased by exactly one", async () => {
      await expect.poll(() => pageGallery.itemsCount()).toBe(initialCount - 1);
    });
  });

  test("As a user, I can duplicate a file and download it", async ({ pageGallery, clientFiles }) => {
    const name = `${uniqueName("e2e-gallery-dup")}.png`;
    const expectedDuplicateName = name.replace(/\.png$/, "_copy.png");

    await test.step("Given an uploaded file", async () => {
      const uploaded = await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
    });

    await test.step("When I duplicate it", async () => {
      await pageGallery.selectFile(name);
      await pageGallery.actions.open();
      await pageGallery.actions.clickAction("Duplicate");
    });

    await test.step("Then both the original and a _copy-suffixed duplicate exist", async () => {
      await expect(pageGallery.fileCell(name)).toBeVisible();
      await expect(pageGallery.fileCell(expectedDuplicateName)).toBeVisible();
    });

    await test.step("When I download the original", async () => {
      await pageGallery.selectFile(name);
      const download = await pageGallery.downloadSelected();
      expect(download.suggestedFilename()).toBe(name);
    });
  });

  test("As a user, I can move a file into a folder using the Move dialog", async ({ pageGallery, clientFiles }) => {
    const folderName = uniqueName("e2e-gallery-movetarget");
    const name = `${uniqueName("e2e-gallery-moveme")}.png`;

    await test.step("Given a folder at the Images root and a file inside Api Inbox", async () => {
      await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.openInSection("Images");

      await pageGallery.createFolder(folderName);
      await pageGallery.openFolder("Api Inbox");
      await pageGallery.waitForFile(name);
    });

    await test.step("When I move the file into the folder", async () => {
      await pageGallery.selectFile(name);
      await pageGallery.moveSelectedTo(folderName);
    });

    await test.step("Then the file is no longer inside Api Inbox", async () => {
      await expect(pageGallery.fileCell(name)).toHaveCount(0);
    });

    await test.step("And the file is inside the folder", async () => {
      await pageGallery.openInSection("Images");
      await pageGallery.openFolder(folderName);
      await expect(pageGallery.fileCell(name)).toBeVisible();
    });
  });

  test("As a user, I can export a file — export runs as an async job reported via the notification bell", async ({
    pageGallery,
    clientFiles,
    componentNotifications,
    componentExportWizard,
  }) => {
    const name = `${uniqueName("e2e-gallery-export")}.png`;

    await test.step("Given an uploaded file", async () => {
      const uploaded = await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
    });

    await test.step("When I export it as an XML bundle", async () => {
      await pageGallery.selectFile(name);
      await pageGallery.actions.open();
      await pageGallery.actions.clickAction("Export");
      await componentExportWizard.waitForOpen();
      await componentExportWizard.selectFormat("xml");
      await componentExportWizard.next();
      await componentExportWizard.submit();
    });

    await test.step("Then a completion notification eventually appears", async () => {
      await componentNotifications.waitForBadgeCountInUI(1);
    });
  });
});
