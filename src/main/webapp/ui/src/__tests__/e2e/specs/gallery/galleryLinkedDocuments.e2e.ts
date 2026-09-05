import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { TINY_PNG } from "@/__tests__/e2e/testData";

test.describe("Gallery Linked Documents", () => {
  test("As a user, inserting a file into a document shows that document as a Linked Document on the file in Gallery", async ({
    clientFiles,
    pageWorkspace,
    pageGallery,
  }) => {
    const fileName = "e2e-linked-doc-image.png";

    await test.step("Given a document with an inserted file attachment", async () => {
      await clientFiles.uploadFile({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Images");
      await picker.openFolder("Api Inbox");
      await picker.selectItem(fileName);
      await picker.add();
      const field = await editor.getField("New List of Materials");
      await field.imageElement.waitFor({ state: "visible" });
      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then the file's Gallery info panel lists this document as a Linked Document", async () => {
      await pageGallery.openInSection("Images");
      await pageGallery.openFolder("Api Inbox");
      await pageGallery.selectFile(fileName);
      await expect(pageGallery.infoPanel.linkedDocumentsRow("Untitled document")).toBeVisible();
    });
  });
});
