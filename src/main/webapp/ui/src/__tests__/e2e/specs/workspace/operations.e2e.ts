import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Workspace operations`, () => {
  test(`As a user, I can create a document via the Create menu`, async ({ pageWorkspace }) => {
    await test.step("When I create a basic document", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then it appears in the Workspace", async () => {
      if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
      await expect(pageWorkspace.table.row("Untitled document")).toBeVisible();
    });
  });

  test(`As a user, I can create nested folders in one submission, with a document inside the deepest one`, async ({
    pageWorkspace,
  }) => {
    const parent = uniqueName("e2e-ops-nested-parent");
    const child = uniqueName("e2e-ops-nested-child");
    const grandchild = uniqueName("e2e-ops-nested-grandchild");
    const docName = uniqueName("e2e-ops-nested-doc");

    await test.step("When I create a `/`-separated nested folder path", async () => {
      await pageWorkspace.open();
      await pageWorkspace.createFolder(`${parent}/${child}/${grandchild}`, { navigate: true });
    });

    await test.step("Then I land inside the deepest folder", async () => {
      await pageWorkspace.waitUntilBreadcrumbShows(grandchild);
    });

    await test.step("When I create a document inside it", async () => {
      const editor = await pageWorkspace.createBasicDocument();
      await editor.editToolbar.saveAndClose();
      if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
      await pageWorkspace.table.selectRecord("Untitled document");
      await pageWorkspace.selectionBar.rename(docName);
    });

    await test.step("Then all three folder levels and the document are visible by navigating down from Home", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openRecord(parent);
      await expect(pageWorkspace.table.row(child)).toBeVisible();
      await pageWorkspace.table.openRecord(child);
      await expect(pageWorkspace.table.row(grandchild)).toBeVisible();
      await pageWorkspace.table.openRecord(grandchild);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });
  });

  test(`As a user, I can rename a document and a folder`, async ({ pageWorkspace, clientDocuments, clientFolders }) => {
    const docName = uniqueName("e2e-ops-rename-doc");
    const docNewName = uniqueName("e2e-ops-rename-doc-new");
    const folderName = uniqueName("e2e-ops-rename-folder");
    const folderNewName = uniqueName("e2e-ops-rename-folder-new");

    await test.step("Given a document and a folder exist", async () => {
      await clientDocuments.create({ name: docName });
      await clientFolders.create({ name: folderName });
      await pageWorkspace.open();
    });

    await test.step("When I rename the folder", async () => {
      await pageWorkspace.table.selectRecord(folderName);
      await pageWorkspace.selectionBar.rename(folderNewName);
    });

    await test.step("Then the folder shows its new name", async () => {
      await expect(pageWorkspace.table.row(folderNewName)).toBeVisible();
      await expect(pageWorkspace.table.row(folderName)).toHaveCount(0);
      await pageWorkspace.table.deselectRecord(folderNewName);
    });

    await test.step("When I rename the document", async () => {
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.rename(docNewName);
    });

    await test.step("Then the document shows its new name", async () => {
      await expect(pageWorkspace.table.row(docNewName)).toBeVisible();
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
    });
  });

  test(`As a user, duplicating a document or folder creates an independent copy with its contents`, async ({
    pageWorkspace,
    clientDocuments,
    clientFolders,
  }) => {
    const docName = uniqueName("e2e-ops-dup-doc");
    const folderName = uniqueName("e2e-ops-dup-folder");
    const nestedDoc = uniqueName("e2e-ops-dup-nested-doc");

    await test.step("Given a document and a folder (containing a document) exist", async () => {
      await clientDocuments.create({ name: docName });
      const folder = await clientFolders.create({ name: folderName });
      await clientDocuments.create({ name: nestedDoc, parentFolderId: folder.id });
      await pageWorkspace.open();
    });

    await test.step("When I duplicate the document", async () => {
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.clickAction("Duplicate");
    });

    await test.step("Then both the original and the copy exist independently", async () => {
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
      await expect(pageWorkspace.table.row(`${docName}_Copy`)).toBeVisible();
      await pageWorkspace.searchBar.clearSearch();
    });

    await test.step("When I duplicate the folder", async () => {
      await pageWorkspace.table.selectRecord(folderName);
      await pageWorkspace.selectionBar.clickAction("Duplicate");
    });

    await test.step("Then the copy exists with its contents intact", async () => {
      await expect(pageWorkspace.table.row(folderName)).toBeVisible();
      await expect(pageWorkspace.table.row(`${folderName}_Copy`)).toBeVisible();
      await pageWorkspace.table.openRecord(`${folderName}_Copy`);
      await expect(pageWorkspace.table.row(nestedDoc)).toBeVisible();
    });
  });

  test(`As a user, closing a document returns to the folder it was opened from`, async ({
    pageWorkspace,
    pageDocument,
    clientDocuments,
    clientFolders,
  }) => {
    const folderName = uniqueName("e2e-ops-close-folder");
    const docName = uniqueName("e2e-ops-close-doc");

    await test.step("Given a folder containing a document exists", async () => {
      const folder = await clientFolders.create({ name: folderName });
      await clientDocuments.create({ name: docName, parentFolderId: folder.id });
      await pageWorkspace.open();
    });

    await test.step("When I open the folder, then the document, then close it", async () => {
      await pageWorkspace.table.openRecord(folderName);
      await pageWorkspace.waitUntilBreadcrumbShows(folderName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      await pageDocument.close();
    });

    await test.step("Then I'm back inside the folder, not Home", async () => {
      await pageWorkspace.waitUntilBreadcrumbShows(folderName);
    });
  });

  test(`As a user, the print button is available in both edit and view mode`, async ({ pageWorkspace }) => {
    const editor = await test.step("Given I create a document", async () => {
      await pageWorkspace.open();
      return pageWorkspace.createBasicDocument();
    });

    await test.step("Then the print button is present in edit mode", async () => {
      await expect(editor.toolbar.actions.printButton).toBeVisible();
    });

    await test.step("When I save and view it", async () => {
      await editor.saveAndView();
    });

    await test.step("Then the print button is present in view mode too", async () => {
      await expect(editor.toolbar.actions.printButton).toBeVisible();
    });
  });
});
