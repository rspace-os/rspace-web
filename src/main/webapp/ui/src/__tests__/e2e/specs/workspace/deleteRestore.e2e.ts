import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Workspace delete and restore`, () => {
  test(`As a user, I can delete a folder and restore it`, async ({
    pageWorkspace,
    clientFolders,
    pageDeletedItems,
  }) => {
    const folderName = uniqueName("e2e-delete-folder");

    await test.step("Given a folder exists at Home", async () => {
      await clientFolders.create({ name: folderName });
      await pageWorkspace.open();
    });

    await test.step("When I select and delete it", async () => {
      await pageWorkspace.table.selectRecord(folderName);
      await pageWorkspace.selectionBar.delete();
    });

    await test.step("Then it's gone from the Workspace", async () => {
      await expect(pageWorkspace.table.row(folderName)).toHaveCount(0);
    });

    await test.step("When I restore it from Deleted Items", async () => {
      await pageDeletedItems.open();
      await pageDeletedItems.isLoaded();
      await pageDeletedItems.search(folderName);
      await pageDeletedItems.restore(folderName);
    });

    await test.step("Then it's back in the Workspace", async () => {
      await pageWorkspace.open();
      await expect(pageWorkspace.table.row(folderName)).toBeVisible();
    });
  });

  test(`As a user, I can delete a notebook and restore it`, async ({
    pageWorkspace,
    clientFolders,
    pageDeletedItems,
  }) => {
    const notebookName = uniqueName("e2e-delete-notebook");

    await test.step("Given a notebook exists at Home", async () => {
      await clientFolders.create({ name: notebookName, notebook: true });
      await pageWorkspace.open();
    });

    await test.step("When I select and delete it", async () => {
      await pageWorkspace.table.selectRecord(notebookName);
      await pageWorkspace.selectionBar.delete();
    });

    await test.step("Then it's gone from the Workspace", async () => {
      await expect(pageWorkspace.table.row(notebookName)).toHaveCount(0);
    });

    await test.step("When I restore it from Deleted Items", async () => {
      await pageDeletedItems.open();
      await pageDeletedItems.isLoaded();
      await pageDeletedItems.search(notebookName);
      await pageDeletedItems.restore(notebookName);
    });

    await test.step("Then it's back in the Workspace", async () => {
      await pageWorkspace.open();
      await expect(pageWorkspace.table.row(notebookName)).toBeVisible();
    });
  });

  test(`As a user, I can delete a document via the keyboard and confirm via Enter`, async ({
    pageWorkspace,
    clientDocuments,
    pageDeletedItems,
  }) => {
    const docName = uniqueName("e2e-delete-doc-kbd");

    await test.step("Given a document exists and is selected", async () => {
      await clientDocuments.create({ name: docName });
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
    });

    await test.step("When I delete it and confirm via the keyboard", async () => {
      await pageWorkspace.selectionBar.delete({ viaKeyboard: true });
    });

    await test.step("Then it's gone from the Workspace and present in Deleted Items", async () => {
      await pageWorkspace.searchBar.clearSearch();
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
      await pageDeletedItems.open();
      await pageDeletedItems.isLoaded();
      await pageDeletedItems.search(docName);
      await expect(pageDeletedItems.row(docName)).toBeVisible();
    });
  });
});
