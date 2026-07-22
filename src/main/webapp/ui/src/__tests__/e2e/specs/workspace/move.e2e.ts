import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { uniqueName } from "@/__tests__/e2e/testData";

async function createTopLevelDoc(pageWorkspace: WorkspacePage, name: string): Promise<void> {
  await pageWorkspace.open();
  const editor = await pageWorkspace.createBasicDocument();
  await editor.editToolbar.saveAndClose();
  if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
  await pageWorkspace.table.selectRecord("Untitled document");
  await pageWorkspace.selectionBar.rename(name);
  await pageWorkspace.table.deselectRecord(name);
}

test.describe(`Workspace move`, () => {
  test.beforeEach(async ({ browserName }) => {
    test.skip(browserName === "webkit", "Move dialog folder-tree clicks don't register on webkit");
  });

  test(`As a user, I can move a document between folders and back Home`, async ({ pageWorkspace, clientFolders }) => {
    const folder1 = uniqueName("e2e-move-folder1");
    const folder2 = uniqueName("e2e-move-folder2");
    const docName = uniqueName("e2e-move-doc");

    await test.step("Given a document and two folders exist at Home", async () => {
      await clientFolders.create({ name: folder1 });
      await clientFolders.create({ name: folder2 });
      await createTopLevelDoc(pageWorkspace, docName);
    });

    await test.step("When I move the document into folder1", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(folder1);
      await moveDialog.clickMove();
    });

    await test.step("Then it's gone from Home and present inside folder1", async () => {
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
      await pageWorkspace.table.openRecord(folder1);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    await test.step("When I move it straight on to folder2", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(folder2);
      await moveDialog.clickMove();
    });

    await test.step("Then it's gone from folder1 and present inside folder2", async () => {
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
      await pageWorkspace.open();
      await pageWorkspace.table.openRecord(folder2);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    await test.step("When I move it back to Home", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickMove();
    });

    await test.step("Then it's back at Home", async () => {
      await pageWorkspace.open();
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });
  });

  test(`As a user, I can move multiple items to a folder in one action`, async ({ pageWorkspace, clientFolders }) => {
    test.slow();
    const folder = uniqueName("e2e-move-multi-source");
    const target = uniqueName("e2e-move-multi-target");
    const doc1 = uniqueName("e2e-move-multi-doc1");
    const doc2 = uniqueName("e2e-move-multi-doc2");

    await test.step("Given two documents and a folder exist at Home, plus a target folder", async () => {
      await createTopLevelDoc(pageWorkspace, doc1);
      await createTopLevelDoc(pageWorkspace, doc2);
      await clientFolders.create({ name: folder });
      await clientFolders.create({ name: target });
      await pageWorkspace.open();
    });

    await test.step("When I select all three and move them to the target folder", async () => {
      await pageWorkspace.table.selectRecords(doc1, doc2, folder);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(target);
      await moveDialog.clickMove();
    });

    await test.step("Then all three appear inside the target folder", async () => {
      await pageWorkspace.table.openRecord(target);
      await expect(pageWorkspace.table.row(doc1)).toBeVisible();
      await expect(pageWorkspace.table.row(doc2)).toBeVisible();
      await expect(pageWorkspace.table.row(folder)).toBeVisible();
    });
  });

  test(`As a user, moving a folder preserves its contents`, async ({
    pageWorkspace,
    clientDocuments,
    clientFolders,
  }) => {
    const sourceFolder = uniqueName("e2e-move-nested-source");
    const targetFolder = uniqueName("e2e-move-nested-target");
    const nestedDoc = uniqueName("e2e-move-nested-doc");

    await test.step("Given a folder containing a document, and a separate target folder", async () => {
      const source = await clientFolders.create({ name: sourceFolder });
      await clientDocuments.create({ name: nestedDoc, parentFolderId: source.id });
      await clientFolders.create({ name: targetFolder });
      await pageWorkspace.open();
    });

    await test.step("When I move the source folder into the target folder", async () => {
      await pageWorkspace.table.selectRecord(sourceFolder);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(targetFolder);
      await moveDialog.clickMove();
    });

    await test.step("Then the source folder (with its document intact) is now inside the target folder", async () => {
      await expect(pageWorkspace.table.row(sourceFolder)).toHaveCount(0);
      await pageWorkspace.table.openRecord(targetFolder);
      await expect(pageWorkspace.table.row(sourceFolder)).toBeVisible();
      await pageWorkspace.table.openRecord(sourceFolder);
      await expect(pageWorkspace.table.row(nestedDoc)).toBeVisible();
    });
  });

  test(`As a user, the move dialog's folder tree can be sorted by name and by date, ascending or descending`, async ({
    pageWorkspace,
    clientFolders,
  }) => {
    const parent = uniqueName("e2e-move-sort-parent");
    const names = ["charlie", "alpha", "bravo"].map((n) => uniqueName(`e2e-move-sort-${n}`));
    const docToMove = uniqueName("e2e-move-sort-doctomove");

    await test.step("Given a parent folder containing 3 children created in a fixed order, and a document to move", async () => {
      const parentFolder = await clientFolders.create({ name: parent });
      for (const name of names) {
        await clientFolders.create({ name, parentFolderId: parentFolder.id });
      }
      await createTopLevelDoc(pageWorkspace, docToMove);
    });

    await test.step("Then the move dialog's tree defaults to sorting by name, ascending", async () => {
      await pageWorkspace.table.selectRecord(docToMove);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(parent);
      const byNameAsc = [...names].sort();
      await expect.poll(() => moveDialog.visibleItemNames()).toEqual(byNameAsc);

      await test.step("And descending name order reverses it", async () => {
        await moveDialog.setOrdering("name", "DESC");
        await expect.poll(() => moveDialog.visibleItemNames()).toEqual([...byNameAsc].reverse());
      });

      await test.step("And sorting by creation date ascending matches creation order", async () => {
        await moveDialog.setOrdering("creationdate", "ASC");
        await expect.poll(() => moveDialog.visibleItemNames()).toEqual(names);
      });

      await test.step("And sorting by creation date descending reverses creation order", async () => {
        await moveDialog.setOrdering("creationdate", "DESC");
        await expect.poll(() => moveDialog.visibleItemNames()).toEqual([...names].reverse());
      });

      await moveDialog.cancel();
    });
  });

  test(`As a user, I can move a document into my own notebook and back out`, async ({
    pageWorkspace,
    clientFolders,
  }) => {
    const notebookName = uniqueName("e2e-move-notebook");
    const docName = uniqueName("e2e-move-into-notebook-doc");

    await test.step("Given a notebook and a document exist at Home", async () => {
      await clientFolders.create({ name: notebookName, notebook: true });
      await createTopLevelDoc(pageWorkspace, docName);
    });

    await test.step("When I move the document into the notebook", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickFolder(notebookName);
      await moveDialog.clickMove();
    });

    await test.step("Then the document is gone from Home and present inside the notebook", async () => {
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
      await pageWorkspace.table.openRecord(notebookName);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    await test.step("When I move it back out to Home", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const moveDialog = await pageWorkspace.selectionBar.move();
      await moveDialog.clickFolder("Home");
      await moveDialog.clickMove();
    });

    await test.step("Then it's back at Home and gone from the notebook", async () => {
      await pageWorkspace.open();
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
      await pageWorkspace.table.openRecord(notebookName);
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
    });
  });
});
