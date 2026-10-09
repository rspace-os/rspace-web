import { expect } from "@playwright/test";
import type { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

async function expectSignedContentProtected(
  client: DocumentsClient,
  id: number,
  expectedContent: string,
): Promise<void> {
  const document = await client.getById(id);
  expect(document.fields).toHaveLength(1);
  expect(document.fields[0].content).toContain(expectedContent);

  await expect(
    client.update(id, {
      name: document.name,
      fields: [{ id: document.fields[0].id, content: "Attempted change to signed content" }],
    }),
  ).rejects.toMatchObject({
    status: 400,
    body: { errors: [`doc: Document ${id} is signed and cannot be altered`] },
  });
  const unchanged = await client.getById(id);
  expect(unchanged.fields).toEqual(document.fields);
}

test.describe("Notebook sharing and signing", () => {
  test("A document owner can sign entries without a witness, and a PI can sign a shared entry with a witness", async ({
    flowDocumentSession,
    appUser,
    pageWorkspace,
    pageNotebook,
    clientSysadmin,
    clientDocuments,
  }) => {
    test.setTimeout(120_000);
    const groupName = uniqueName("e2e-sign-group");

    const docOwner = await test.step("Given a document owner exists in the same lab group as the PI", async () => {
      const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSignOwner", "SignOwner");
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: user.username, roleInGroup: "DEFAULT" },
        ],
      });
      return user;
    });

    const owner = await flowDocumentSession(docOwner);
    const firstContent = alphaNumericUnique("FirstUnsignedEntry");
    const secondContent = alphaNumericUnique("SecondSignedEntry");
    const editedFirstContent = alphaNumericUnique("EditedUnsignedEntry");

    const notebookName = uniqueName("e2e-sign-nb");
    const entries = await test.step("When the document owner creates a notebook with two entries", async () => {
      await owner.workspace.createNotebook(notebookName);
      const entry1 = await owner.notebook.addEntry();
      const firstId = entry1.getId();
      await (await entry1.getField("", 0)).fill(firstContent);
      await entry1.editToolbar.saveAndClose();
      await owner.notebook.isLoaded();
      const entry2 = await owner.notebook.addEntry();
      const secondId = entry2.getId();
      await (await entry2.getField("", 0)).fill(secondContent);
      await entry2.editToolbar.saveAndClose();
      await owner.notebook.isLoaded();
      return { firstId, secondId };
    });

    await test.step("And signs the currently-selected entry without a witness", async () => {
      await owner.notebook.sign(docOwner.password);
    });

    await test.step("Then that entry is marked signed and can no longer be re-signed", async () => {
      await expect(owner.notebook.signedStatus.first()).toBeVisible();
      await expect(owner.notebook.toolbar.signButton).toBeHidden();
      await expectSignedContentProtected(owner.documents, entries.secondId, secondContent);
    });

    await test.step("And the other entry is still unsigned", async () => {
      await owner.notebook.previousEntry();
      await expect(owner.notebook.signedStatus).toHaveCount(0);
      await expect(owner.notebook.toolbar.signButton).toBeVisible();
      const editor = await owner.notebook.enterEditMode();
      expect(editor.getId()).toBe(entries.firstId);
      await (await editor.getField("", 0)).fill(editedFirstContent);
      await editor.editToolbar.saveAndClose();
      await owner.notebook.isLoaded();
      expect((await owner.documents.getById(entries.firstId)).fields[0].content).toContain(editedFirstContent);
    });

    await test.step("When the document owner signs the first entry too", async () => {
      await owner.notebook.sign(docOwner.password);
      await expect(owner.notebook.signedStatus.first()).toBeVisible();
      await expect(owner.notebook.toolbar.signButton).toBeHidden();
      await expectSignedContentProtected(owner.documents, entries.firstId, editedFirstContent);
    });

    const sharedNotebookName = uniqueName("e2e-sign-shared-nb");
    const sharedContent = alphaNumericUnique("SharedSignedEntry");
    const sharedEntryId = await test.step("Given the PI creates and shares a notebook with the group", async () => {
      await pageWorkspace.open();
      await pageWorkspace.createNotebook(sharedNotebookName);
      const entry = await pageNotebook.addEntry();
      const id = entry.getId();
      await (await entry.getField("", 0)).fill(sharedContent);
      await entry.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
      await pageWorkspace.open();
      await pageWorkspace.table.selectRecord(sharedNotebookName);
      const shareDialog = await pageWorkspace.selectionBar.share();
      await shareDialog.addRecipient(groupName);
      await shareDialog.setPermission(groupName, "EDIT");
      await shareDialog.save();
      return id;
    });

    await test.step("When the PI signs the shared entry with the document owner as witness", async () => {
      await pageWorkspace.table.openNotebook(sharedNotebookName);
      await pageNotebook.isLoaded();
      await pageNotebook.signWithWitness(appUser.password, docOwner.username);
    });

    await test.step("Then the entry is marked signed and can no longer be re-signed, awaiting the witness", async () => {
      await expect(pageNotebook.signedStatus.first()).toBeVisible();
      await expect(pageNotebook.toolbar.signButton).toBeHidden();
      await expect(pageNotebook.witnessedStatus).toBeHidden();
      await expectSignedContentProtected(clientDocuments, sharedEntryId, sharedContent);
    });

    await test.step("When the document owner, as witness, confirms the signing", async () => {
      await owner.workspace.open();
      await owner.workspace.searchBar.search(sharedNotebookName);
      await owner.workspace.table.openNotebook(sharedNotebookName);
      await owner.notebook.isLoaded();
      await expect(owner.notebook.toolbar.witnessButton).toBeVisible();
      await owner.notebook.confirmWitness(docOwner.password);
    });

    await test.step("Then the entry is fully witnessed", async () => {
      await expect(owner.notebook.witnessedStatus).toBeVisible();
      await expect(owner.notebook.toolbar.witnessButton).toBeHidden();
    });

    await test.step("And the PI sees the same fully-witnessed status on reopening the entry", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(sharedNotebookName);
      await pageNotebook.isLoaded();
      await expect(pageNotebook.witnessedStatus).toBeVisible();
    });
  });

  test("A grouped user can share a document and a notebook with their group; the PI then sees them", async ({
    flowDocumentSession,
    appUser,
    pageWorkspace,
    clientSysadmin,
  }) => {
    test.setTimeout(120_000);
    const groupName = uniqueName("e2e-share-group");
    const docName = uniqueName("e2e-share-doc");
    const notebookName = uniqueName("e2e-share-nb");

    const { groupedUser, noGroupUser } =
      await test.step("Given a grouped user (with the PI) and an ungrouped user each own a document and a notebook", async () => {
        const groupedUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareGrouped", "ShareGrouped");
        const noGroupUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareNoGroup", "ShareNoGroup");
        await clientSysadmin.createGroup({
          displayName: groupName,
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: groupedUser.username, roleInGroup: "DEFAULT" },
          ],
        });
        return { groupedUser, noGroupUser };
      });

    const sharedWithPiBefore =
      await test.step("And I note how many items are currently shared with the PI", async () => {
        await pageWorkspace.open();
        await pageWorkspace.toolbar.toggleFilter("shared");
        const count = await pageWorkspace.table.rowCount();
        await pageWorkspace.toolbar.toggleFilter("shared");
        return count;
      });

    const noGroup = await flowDocumentSession(noGroupUser);
    const grouped = await flowDocumentSession(groupedUser);

    await test.step("Given the ungrouped user has a document and a notebook of their own", async () => {
      await noGroup.workspace.open();
      const doc = await noGroup.workspace.createBasicDocument();
      await doc.header.rename(docName);
      await doc.editToolbar.saveAndClose();
      await noGroup.workspace.waitUntilLoaded();
      await noGroup.workspace.createNotebook(notebookName);
    });

    await test.step("Then Share is not available to them for either", async () => {
      await noGroup.workspace.open();
      await noGroup.workspace.table.selectRecord(docName);
      expect(await noGroup.workspace.selectionBar.isActionVisible("Share")).toBe(false);
      await noGroup.workspace.table.deselectRecord(docName);
      await noGroup.workspace.table.selectRecord(notebookName);
      expect(await noGroup.workspace.selectionBar.isActionVisible("Share")).toBe(false);
    });

    await test.step("Given the grouped user has a document and a notebook of their own", async () => {
      await grouped.workspace.open();
      const doc = await grouped.workspace.createBasicDocument();
      await doc.header.rename(docName);
      await doc.editToolbar.saveAndClose();
      await grouped.workspace.waitUntilLoaded();
      const notebook = await grouped.workspace.createNotebook(notebookName);

      await test.step("Then Share is hidden in the notebook editor while it's still empty", async () => {
        await expect(notebook.toolbar.shareButton).toBeHidden();
      });
    });

    await test.step("Then Share is available to them, and they share both with the group", async () => {
      await grouped.workspace.open();
      await grouped.workspace.table.selectRecord(docName);
      expect(await grouped.workspace.selectionBar.isActionVisible("Share")).toBe(true);
      const docShare = await grouped.workspace.selectionBar.share();
      await docShare.addRecipient(groupName);
      await docShare.save();

      await grouped.workspace.open();
      await grouped.workspace.table.selectRecord(notebookName);
      expect(await grouped.workspace.selectionBar.isActionVisible("Share")).toBe(true);
      const nbShare = await grouped.workspace.selectionBar.share();
      await nbShare.addRecipient(groupName);
      await nbShare.save();
    });

    await test.step("And re-sharing the notebook with the same group is disabled", async () => {
      await grouped.workspace.open();
      await grouped.workspace.table.selectRecord(notebookName);
      const reShare = await grouped.workspace.selectionBar.share();
      await reShare.search(groupName);
      expect(await reShare.isOptionDisabled(groupName)).toBe(true);
      await reShare.close();
    });

    await test.step("Then the PI now sees both the shared document and notebook", async () => {
      await pageWorkspace.open();
      await pageWorkspace.toolbar.toggleFilter("shared");
      await expect.poll(() => pageWorkspace.table.rowCount()).toBeGreaterThanOrEqual(sharedWithPiBefore + 2);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
      await expect(pageWorkspace.table.row(notebookName)).toBeVisible();
    });
  });

  test("As a user, I can open a notebook directly by navigating to its global ID URL", async ({
    clientFolders,
    clientDocuments,
    pageNotebook,
  }) => {
    const notebookName = uniqueName("e2e-globalid-nb");

    const notebook = await test.step("Given a notebook exists", async () => {
      return clientFolders.create({ name: notebookName, notebook: true });
    });
    const entryName = uniqueName("e2e-globalid-entry");
    const content = alphaNumericUnique("GlobalIdNotebookContent");
    await clientDocuments.create({ name: entryName, parentFolderId: notebook.id, fields: [{ content }] });

    await test.step("When I navigate directly to its global ID URL", async () => {
      await pageNotebook.openByGlobalId(notebook);
    });

    await test.step("Then the notebook editor loads", async () => {
      await expect(pageNotebook.header.name).toHaveText(entryName);
      await expect(pageNotebook.entryContent).toBeVisible();
      await expect(pageNotebook.entryContent).toContainText(content);
      expect(await pageNotebook.entryStrip.getEntryCount()).toEqual({ current: 1, total: 1 });
    });
  });
});
