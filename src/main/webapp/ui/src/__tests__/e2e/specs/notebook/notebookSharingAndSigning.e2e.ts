import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { loginAsWorkspaceUser } from "@/__tests__/e2e/fixtures/flows/userSessions";
import { NotebookPage } from "@/__tests__/e2e/pageObjects/notebook/NotebookPage";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Notebook sharing and signing", () => {
  test("A document owner can sign entries without a witness, and a PI can sign a shared entry with a witness", async ({
    browser,
    browserContextOptions,
    appUser,
    pageWorkspace,
    pageNotebook,
    clientSysadmin,
  }) => {
    test.setTimeout(120_000);
    const groupName = uniqueName("e2e-sign-group");

    const docOwnerUsername =
      await test.step("Given a document owner exists in the same lab group as the PI", async () => {
        const { username } = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSignOwner", "SignOwner");
        await clientSysadmin.createGroup({
          displayName: groupName,
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username, roleInGroup: "DEFAULT" },
          ],
        });
        return username;
      });

    const owner = await loginAsWorkspaceUser(browser, browserContextOptions, docOwnerUsername);
    try {
      const ownerWorkspace = owner.workspace;
      const ownerNotebook = new NotebookPage(owner.page);

      const notebookName = uniqueName("e2e-sign-nb");
      await test.step("When the document owner creates a notebook with two entries", async () => {
        await ownerWorkspace.createNotebook(notebookName);
        const entry1 = await ownerNotebook.addEntry();
        await entry1.editToolbar.saveAndClose();
        await ownerNotebook.isLoaded();
        const entry2 = await ownerNotebook.addEntry();
        await entry2.editToolbar.saveAndClose();
        await ownerNotebook.isLoaded();
      });

      await test.step("And signs the currently-selected entry without a witness", async () => {
        await ownerNotebook.sign(DYNAMIC_USER_PASSWORD);
      });

      await test.step("Then that entry is marked signed and can no longer be re-signed", async () => {
        expect(await ownerNotebook.isSigned()).toBe(true);
        expect(await ownerNotebook.canSign()).toBe(false);
      });

      await test.step("And the other entry is still unsigned", async () => {
        await ownerNotebook.previousEntry();
        expect(await ownerNotebook.isSigned()).toBe(false);
        expect(await ownerNotebook.canSign()).toBe(true);
      });

      await test.step("When the document owner signs the second entry too", async () => {
        await ownerNotebook.sign(DYNAMIC_USER_PASSWORD);
        expect(await ownerNotebook.isSigned()).toBe(true);
        expect(await ownerNotebook.canSign()).toBe(false);
      });

      const sharedNotebookName = uniqueName("e2e-sign-shared-nb");
      await test.step("Given the PI creates and shares a notebook with the group", async () => {
        await pageWorkspace.open();
        await pageWorkspace.createNotebook(sharedNotebookName);
        const entry = await pageNotebook.addEntry();
        await entry.editToolbar.saveAndClose();
        await pageNotebook.isLoaded();
        await pageWorkspace.open();
        await pageWorkspace.table.selectRecord(sharedNotebookName);
        const shareDialog = await pageWorkspace.selectionBar.share();
        await shareDialog.addRecipient(groupName);
        await shareDialog.setPermission(groupName, "EDIT");
        await shareDialog.save();
      });

      await test.step("When the PI signs the shared entry with the document owner as witness", async () => {
        await pageWorkspace.table.openNotebook(sharedNotebookName);
        await pageNotebook.isLoaded();
        await pageNotebook.signWithWitness(appUser.password, docOwnerUsername);
      });

      await test.step("Then the entry is marked signed and can no longer be re-signed, awaiting the witness", async () => {
        expect(await pageNotebook.isSigned()).toBe(true);
        expect(await pageNotebook.canSign()).toBe(false);
        expect(await pageNotebook.isWitnessed()).toBe(false);
      });

      await test.step("When the document owner, as witness, confirms the signing", async () => {
        await ownerWorkspace.open();
        await ownerWorkspace.searchBar.search(sharedNotebookName);
        await ownerWorkspace.table.openNotebook(sharedNotebookName);
        await ownerNotebook.isLoaded();
        expect(await ownerNotebook.canWitness()).toBe(true);
        await ownerNotebook.confirmWitness(DYNAMIC_USER_PASSWORD);
      });

      await test.step("Then the entry is fully witnessed", async () => {
        expect(await ownerNotebook.isWitnessed()).toBe(true);
        expect(await ownerNotebook.canWitness()).toBe(false);
      });

      await test.step("And the PI sees the same fully-witnessed status on reopening the entry", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openNotebook(sharedNotebookName);
        await pageNotebook.isLoaded();
        expect(await pageNotebook.isWitnessed()).toBe(true);
      });
    } finally {
      await owner.close();
    }
  });

  test("A grouped user can share a document and a notebook with their group; the PI then sees them", async ({
    browser,
    browserContextOptions,
    appUser,
    pageWorkspace,
    clientSysadmin,
  }) => {
    test.setTimeout(120_000);
    const groupName = uniqueName("e2e-share-group");
    const docName = uniqueName("e2e-share-doc");
    const notebookName = uniqueName("e2e-share-nb");

    const { groupedUsername, noGroupUsername } =
      await test.step("Given a grouped user (with the PI) and an ungrouped user each own a document and a notebook", async () => {
        const { username: groupedUsername } = await createDynamicUser(
          clientSysadmin,
          "ROLE_USER",
          "e2eShareGrouped",
          "ShareGrouped",
        );
        const { username: noGroupUsername } = await createDynamicUser(
          clientSysadmin,
          "ROLE_USER",
          "e2eShareNoGroup",
          "ShareNoGroup",
        );
        await clientSysadmin.createGroup({
          displayName: groupName,
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: groupedUsername, roleInGroup: "DEFAULT" },
          ],
        });
        return { groupedUsername, noGroupUsername };
      });

    const sharedWithPiBefore =
      await test.step("And I note how many items are currently shared with the PI", async () => {
        await pageWorkspace.open();
        await pageWorkspace.toolbar.toggleFilter("shared");
        const count = await pageWorkspace.table.rowCount();
        await pageWorkspace.toolbar.toggleFilter("shared");
        return count;
      });

    const noGroup = await loginAsWorkspaceUser(browser, browserContextOptions, noGroupUsername);
    const grouped = await loginAsWorkspaceUser(browser, browserContextOptions, groupedUsername);
    try {
      const noGroupWorkspace = noGroup.workspace;

      await test.step("Given the ungrouped user has a document and a notebook of their own", async () => {
        await noGroupWorkspace.open();
        const doc = await noGroupWorkspace.createBasicDocument();
        await doc.header.rename(docName);
        await doc.editToolbar.saveAndClose();
        await noGroupWorkspace.waitUntilLoaded();
        await noGroupWorkspace.createNotebook(notebookName);
      });

      await test.step("Then Share is not available to them for either", async () => {
        await noGroupWorkspace.open();
        await noGroupWorkspace.table.selectRecord(docName);
        expect(await noGroupWorkspace.selectionBar.isActionVisible("Share")).toBe(false);
        await noGroupWorkspace.table.deselectRecord(docName);
        await noGroupWorkspace.table.selectRecord(notebookName);
        expect(await noGroupWorkspace.selectionBar.isActionVisible("Share")).toBe(false);
      });

      const groupedWorkspace = grouped.workspace;

      await test.step("Given the grouped user has a document and a notebook of their own", async () => {
        await groupedWorkspace.open();
        const doc = await groupedWorkspace.createBasicDocument();
        await doc.header.rename(docName);
        await doc.editToolbar.saveAndClose();
        await groupedWorkspace.waitUntilLoaded();
        const notebook = await groupedWorkspace.createNotebook(notebookName);

        await test.step("Then Share is hidden in the notebook editor while it's still empty", async () => {
          await expect(notebook.toolbar.shareButton).toBeHidden();
        });
      });

      await test.step("Then Share is available to them, and they share both with the group", async () => {
        await groupedWorkspace.open();
        await groupedWorkspace.table.selectRecord(docName);
        expect(await groupedWorkspace.selectionBar.isActionVisible("Share")).toBe(true);
        const docShare = await groupedWorkspace.selectionBar.share();
        await docShare.addRecipient(groupName);
        await docShare.save();

        await groupedWorkspace.open();
        await groupedWorkspace.table.selectRecord(notebookName);
        expect(await groupedWorkspace.selectionBar.isActionVisible("Share")).toBe(true);
        const nbShare = await groupedWorkspace.selectionBar.share();
        await nbShare.addRecipient(groupName);
        await nbShare.save();
      });

      await test.step("And re-sharing the notebook with the same group is disabled", async () => {
        await groupedWorkspace.open();
        await groupedWorkspace.table.selectRecord(notebookName);
        const reShare = await groupedWorkspace.selectionBar.share();
        await reShare.search(groupName);
        expect(await reShare.isOptionDisabled(groupName)).toBe(true);
        await reShare.close();
      });
    } finally {
      await noGroup.close();
      await grouped.close();
    }

    await test.step("Then the PI now sees both the shared document and notebook", async () => {
      await pageWorkspace.open();
      await pageWorkspace.toolbar.toggleFilter("shared");
      await expect.poll(() => pageWorkspace.table.rowCount()).toBeGreaterThanOrEqual(sharedWithPiBefore + 2);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
      await expect(pageWorkspace.table.row(notebookName)).toBeVisible();
    });
  });

  test("As a user, I can open a notebook directly by navigating to its global ID URL", async ({
    page,
    clientFolders,
  }) => {
    const notebookName = uniqueName("e2e-globalid-nb");

    const notebook = await test.step("Given a notebook exists", async () => {
      return clientFolders.create({ name: notebookName, notebook: true });
    });

    await test.step("When I navigate directly to its global ID URL", async () => {
      await page.goto(`/globalId/${notebook.globalId}`);
    });

    await test.step("Then the notebook editor loads", async () => {
      await page.waitForURL("**/notebookEditor/**");
    });
  });
});
