import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { TINY_PNG } from "@/__tests__/e2e/testData";

test.describe("Gallery Linked Documents", () => {
  test.beforeEach(async ({ browserName }) => {
    test.skip(browserName === "webkit", "File upload 500s on webkit — real bug");
  });

  test("As a user, inserting a file into a document shows that document as a Linked Document on the file in Gallery", async ({
    pageWorkspace,
    pageGallery,
  }) => {
    const fileName = "e2e-linked-doc-image.png";

    await test.step("Given a document with an inserted file attachment", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("New List of Materials");
      await field.insertFileAttachment({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });
      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then the file's Gallery info panel lists this document as a Linked Document", async () => {
      await pageGallery.openInSection("Images");
      await pageGallery.selectFile(fileName);
      await expect(pageGallery.infoPanel.linkedDocumentsRow("Untitled document")).toBeVisible();
    });
  });
});
