import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Workspace views and navigation`, () => {
  test(`As a user, list and tree views show the same records, including through a rename with quote characters`, async ({
    pageWorkspace,
    clientFolders,
    clientDocuments,
  }) => {
    const folderName = uniqueName("e2e-nav-folder");
    const nestedDocName = uniqueName("e2e-nav-nested-doc");

    await test.step("Given a folder containing a document exists", async () => {
      const folder = await clientFolders.create({ name: folderName });
      await clientDocuments.create({ name: nestedDocName, parentFolderId: folder.id });
    });

    await test.step("Then both appear in list view", async () => {
      await pageWorkspace.open();
      await expect(pageWorkspace.table.row(folderName)).toBeVisible();

      await pageWorkspace.searchBar.search(nestedDocName);
      await expect(pageWorkspace.table.row(nestedDocName)).toBeVisible();
      await pageWorkspace.searchBar.clearSearch();
    });

    await test.step("And both appear in tree view too, with the document nested under the folder", async () => {
      await pageWorkspace.toolbar.switchLayout("tree");
      await expect(pageWorkspace.tree.item(folderName)).toBeVisible();
      await pageWorkspace.tree.expand(folderName);
      await expect(pageWorkspace.tree.item(nestedDocName)).toBeVisible();
    });

    await test.step("Then renaming the document to a name containing quote characters still works, and the tree reflects it", async () => {
      const newName = uniqueName(`e2e-nav-doc-"quoted"`);
      await pageWorkspace.open();
      await pageWorkspace.toolbar.switchLayout("list");
      await pageWorkspace.searchBar.search(nestedDocName);
      await pageWorkspace.table.selectRecord(nestedDocName);
      await pageWorkspace.selectionBar.rename(newName);
      await expect(pageWorkspace.table.row(newName)).toBeVisible();
      await pageWorkspace.searchBar.clearSearch();

      await pageWorkspace.toolbar.switchLayout("tree");
      await pageWorkspace.tree.expand(folderName);
      await expect(pageWorkspace.tree.item(newName)).toBeVisible();
      await expect(pageWorkspace.tree.item(nestedDocName)).toHaveCount(0);
    });
  });

  test(`As a user, the context-sensitive selection bar reflects what's currently selected`, async ({
    pageWorkspace,
    clientDocuments,
    clientFolders,
  }) => {
    const sharedPrefix = uniqueName("e2e-nav-ctx");
    const doc1 = `${sharedPrefix}-doc1`;
    const doc2 = `${sharedPrefix}-doc2`;
    const folder1 = `${sharedPrefix}-folder1`;
    const folder2 = `${sharedPrefix}-folder2`;

    await test.step("Given two documents and two folders exist", async () => {
      await clientDocuments.create({ name: doc1 });
      await clientDocuments.create({ name: doc2 });
      await clientFolders.create({ name: folder1 });
      await clientFolders.create({ name: folder2 });
      await pageWorkspace.open();

      await pageWorkspace.searchBar.search(sharedPrefix);
    });

    await test.step("Then selecting and deselecting all toggles every row's checkbox", async () => {
      await pageWorkspace.table.selectAllCheckbox.check();
      await expect(pageWorkspace.table.selectAllCheckbox).toBeChecked();
      await pageWorkspace.table.selectAllCheckbox.uncheck();
      await expect(pageWorkspace.table.selectAllCheckbox).not.toBeChecked();
    });

    await test.step("When I select a single document, all actions are available", async () => {
      await pageWorkspace.table.selectRecord(doc1);
      for (const action of ["Duplicate", "Move", "Rename", "Delete", "Export", "CSV", "Revisions"] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(true);
      }
    });

    await test.step("When I add a second document, Rename and Revisions become unavailable", async () => {
      await pageWorkspace.table.selectRecord(doc2);
      expect(await pageWorkspace.selectionBar.isActionVisible("Rename")).toBe(false);
      expect(await pageWorkspace.selectionBar.isActionVisible("Revisions")).toBe(false);
      for (const action of ["Duplicate", "Move", "Delete", "Export", "CSV"] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(true);
      }
      await pageWorkspace.table.deselectRecord(doc1);
      await pageWorkspace.table.deselectRecord(doc2);
    });

    await test.step("When I select a single folder, CSV and Revisions are unavailable (Rename still is)", async () => {
      await pageWorkspace.table.selectRecord(folder1);
      for (const action of ["Duplicate", "Move", "Rename", "Delete", "Export"] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(true);
      }
      expect(await pageWorkspace.selectionBar.isActionVisible("CSV")).toBe(false);
      expect(await pageWorkspace.selectionBar.isActionVisible("Revisions")).toBe(false);
    });

    await test.step("When I add a second folder, Rename also becomes unavailable", async () => {
      await pageWorkspace.table.selectRecord(folder2);
      expect(await pageWorkspace.selectionBar.isActionVisible("Rename")).toBe(false);
      for (const action of ["Duplicate", "Move", "Delete", "Export"] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(true);
      }
      await pageWorkspace.table.deselectRecord(folder1);
      await pageWorkspace.table.deselectRecord(folder2);
      await expect(pageWorkspace.selectionBar.root).toHaveCount(0);
    });
  });

  test(`As a user, the top-level nav tab stays consistent with the current page`, async ({ pageWorkspace }) => {
    await test.step("Given I'm on the Workspace page", async () => {
      await pageWorkspace.open();
    });

    await test.step("Then the Workspace nav tab is active and others are not", async () => {
      expect(await pageWorkspace.header.isActive("Workspace")).toBe(true);
      expect(await pageWorkspace.header.isActive("Gallery")).toBe(false);
      expect(await pageWorkspace.header.isActive("Inventory")).toBe(false);
    });

    await test.step("When I navigate to Gallery", async () => {
      await pageWorkspace.header.navigateTo("Gallery");
    });

    await test.step("Then the Gallery nav tab is active and Workspace is not", async () => {
      expect(await pageWorkspace.header.isActive("Gallery")).toBe(true);
      expect(await pageWorkspace.header.isActive("Workspace")).toBe(false);
    });

    await test.step("When I navigate to Inventory", async () => {
      await pageWorkspace.header.navigateTo("Inventory");
    });

    await test.step("Then the Inventory nav tab is active and Gallery is not", async () => {
      expect(await pageWorkspace.header.isActive("Inventory")).toBe(true);
      expect(await pageWorkspace.header.isActive("Gallery")).toBe(false);
    });
  });

  test(`As a user, Global ID links are present in the workspace table and navigate correctly`, async ({
    pageWorkspace,
    clientDocuments,
    page,
  }) => {
    const docName = uniqueName("e2e-nav-globalid-doc");

    await test.step("Given a document exists", async () => {
      await clientDocuments.create({ name: docName });
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
    });

    await test.step("Then its Global ID link is visible with the expected format", async () => {
      const link = pageWorkspace.table.globalIdLink(docName);
      await expect(link).toBeVisible();
      // `.toHaveText(regExp)` matches raw (untrimmed) textContent, and this
      // legacy anchor is JSP-indented (e.g. "\n    SD5920\n") — trim first.
      const text = (await link.textContent())?.trim() ?? "";
      expect(text).toMatch(/^(SD|NB|FL)\d+$/);
    });

    await test.step("When I click the Global ID link", async () => {
      await pageWorkspace.table.globalIdLink(docName).click();
    });

    await test.step("Then it redirects straight to the document's own editor page (Global ID links never stay on /globalId/...)", async () => {
      await page.waitForURL("**/workspace/editor/structuredDocument/**");
    });
  });

  test(`As a user, tree and list views stay consistent through create, duplicate, rename, and delete`, async ({
    pageWorkspace,
  }) => {
    const docName = uniqueName("e2e-nav-consistency-doc");

    await test.step("Given I create a document (via the Create menu, then rename it to a known unique name)", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const autoName = await editor.header.getName();
      await editor.editToolbar.saveAndClose();
      if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
      await pageWorkspace.searchBar.search(autoName);
      await pageWorkspace.table.selectRecord(autoName);
      await pageWorkspace.selectionBar.rename(docName);
      await pageWorkspace.searchBar.clearSearch();
    });

    await test.step("Then it appears in tree view", async () => {
      await pageWorkspace.toolbar.switchLayout("tree");
      await expect(pageWorkspace.tree.item(docName)).toBeVisible();
    });

    await test.step("When I duplicate it from list view, the copy appears in tree view too", async () => {
      await pageWorkspace.toolbar.switchLayout("list");
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.clickAction("Duplicate");
      await pageWorkspace.searchBar.clearSearch();
      await pageWorkspace.toolbar.switchLayout("tree");
      await expect(pageWorkspace.tree.item(`${docName}_Copy`)).toBeVisible();
    });

    await test.step("When I delete the copy from list view, it disappears from tree view too (the original remains)", async () => {
      await pageWorkspace.toolbar.switchLayout("list");
      await pageWorkspace.searchBar.search(`${docName}_Copy`);
      await pageWorkspace.table.selectRecord(`${docName}_Copy`);
      await pageWorkspace.selectionBar.delete();
      await pageWorkspace.searchBar.clearSearch();
      await pageWorkspace.toolbar.switchLayout("tree");
      await expect(pageWorkspace.tree.item(`${docName}_Copy`)).toHaveCount(0);
      await expect(pageWorkspace.tree.item(docName)).toBeVisible();
    });
  });

  test(`As a user, clicking a folder's icon navigates into it, same as clicking its name`, async ({
    pageWorkspace,
    clientFolders,
  }) => {
    const folderName = uniqueName("e2e-nav-icon-folder");

    await test.step("Given a folder exists at Home", async () => {
      await clientFolders.create({ name: folderName });
      await pageWorkspace.open();
    });

    await test.step("When I click the folder's icon (the Type column's link, not its name)", async () => {
      await pageWorkspace.table.row(folderName).getByRole("link").first().click();
    });

    await test.step("Then it navigates into the folder, same as clicking its name would", async () => {
      await pageWorkspace.waitUntilBreadcrumbShows(folderName);
    });
  });
});
