import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Gallery snippet creation", () => {
  test("As a user, I can create a snippet from content selected in a document", async ({
    pageWorkspace,
    pageGallery,
  }) => {
    const snippetName = uniqueName("e2e-snippet-create");

    const docEditor = await test.step("Given a document with content selected in a field", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("New List of Materials");
      await field.fill("snippet content");
      await field.selectAll();
      return editor;
    });

    await test.step("When I create a snippet from the selection", async () => {
      const snippetDialog = await docEditor.openCreateSnippetDialog();
      await snippetDialog.create(snippetName);
    });

    await test.step("Then the snippet appears in the Gallery's Snippets section", async () => {
      await pageGallery.openInSection("Snippets");
      await expect(pageGallery.fileCell(snippetName)).toBeVisible();
    });
  });
});
