import { expect } from "@playwright/test";
import { expectDocumentUnavailable } from "@/__tests__/e2e/assertions/documents";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Sharing documents", () => {
  test("As a user, sharing a document individually at Read permission makes it read-only for the recipient", async ({
    flowRefreshDocumentSession,
    flowDocumentSession,
    appUser,
    pageWorkspace,
    clientSysadmin,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-share-individual-read");

    const memberUser =
      await test.step("Given a group exists, and I share a document with a member individually at Read", async () => {
        const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareIndMember", "ShareIndMember");
        await clientSysadmin.createGroup({
          displayName: uniqueName("e2e-share-individual-group"),
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: user.username, roleInGroup: "DEFAULT" },
          ],
        });

        await flowRefreshDocumentSession();
        await clientDocuments.create({ name: docName });
        await pageWorkspace.shareRecord(docName, { recipient: user.username, permission: "READ" });

        return user;
      });

    await test.step("Then the recipient sees the document, read-only", async () => {
      const member = await flowDocumentSession(memberUser);
      await member.workspace.searchFor(docName);
      await member.workspace.table.openRecord(docName);
      const memberDoc = member.document;
      await memberDoc.isLoaded();
      expect(await memberDoc.isReadOnly()).toBe(true);
    });
  });

  test("As a user, sharing a document with a group at Edit permission lets a member edit it", async ({
    flowRefreshDocumentSession,
    appUser,
    flowDocumentSession,
    pageWorkspace,
    pageDocument,
    clientSysadmin,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-share-group-edit");
    const editedContent = alphaNumericUnique("e2eShareGroupEditContent");

    const memberUser = await test.step("Given a group exists, and I share a document with it at Edit", async () => {
      const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareGrpMember", "ShareGrpMember");
      const groupName = uniqueName("e2e-share-group-edit-group");
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: user.username, roleInGroup: "DEFAULT" },
        ],
      });

      await flowRefreshDocumentSession();
      await clientDocuments.create({ name: docName });
      await pageWorkspace.shareRecord(docName, { recipient: groupName, permission: "EDIT" });

      return user;
    });

    await test.step("Then the group member can open and edit it", async () => {
      const member = await flowDocumentSession(memberUser);
      await member.workspace.searchFor(docName);
      await member.workspace.table.openRecord(docName);
      const memberDoc = member.document;
      await memberDoc.isLoaded();
      expect(await memberDoc.isReadOnly()).toBe(false);

      const field = await memberDoc.editField("", 0);
      await field.fill(editedContent);
      await field.saveAndFinishEditing();
    });

    await test.step("And I can see the member's edit", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const content = await pageDocument.getFieldViewContent("", 0);
      expect(await content.innerText()).toBe(editedContent);
    });
  });

  test("As a user, deleting a shared document removes it from a group member's workspace too", async ({
    flowRefreshDocumentSession,
    appUser,
    flowDocumentSession,
    pageWorkspace,
    clientSysadmin,
    clientDocuments,
  }) => {
    const docName = alphaNumericUnique("e2eShareDeletePropagation");

    const memberUser = await test.step("Given a group exists, and I share a document with it", async () => {
      const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareDelMember", "ShareDelMember");
      const groupName = uniqueName("e2e-share-delete-group");
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: user.username, roleInGroup: "DEFAULT" },
        ],
      });

      await flowRefreshDocumentSession();
      await clientDocuments.create({ name: docName });
      await pageWorkspace.shareRecord(docName, { recipient: groupName, permission: "EDIT" });

      return user;
    });

    await test.step("Then the member can see it in their workspace before deletion", async () => {
      const member = await flowDocumentSession(memberUser);
      await member.workspace.searchFor(docName);
      await expect(member.workspace.table.row(docName)).toBeVisible();
    });

    await test.step("When I delete the document from my workspace", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.delete();
    });

    await test.step("Then it disappears from the group member's workspace too", async () => {
      const member = await flowDocumentSession(memberUser);
      await member.workspace.searchFor(docName);
      expect(await member.workspace.table.rowCount()).toBe(0);
    });
  });

  test("As a user, I can change a shared document's permission and unshare it via My RSpace > Shared Documents", async ({
    flowRefreshDocumentSession,
    appUser,
    pageWorkspace,
    pageMyRSpace,
    clientSysadmin,
    clientDocuments,
    flowDocumentSession,
  }) => {
    const docX = uniqueName("e2e-share-manage-docx");
    const docY = uniqueName("e2e-share-manage-docy");
    const content = alphaNumericUnique("SharedManagementContent");

    const { member, documents } =
      await test.step("Given a group exists, and I share two documents with a member individually at Read", async () => {
        const created = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareMgmtMember", "ShareMgmtMember");
        await clientSysadmin.createGroup({
          displayName: uniqueName("e2e-share-manage-group"),
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: created.username, roleInGroup: "DEFAULT" },
          ],
        });

        await flowRefreshDocumentSession();
        const documents = await Promise.all(
          [docX, docY].map((name) => clientDocuments.create({ name, fields: [{ content }] })),
        );
        for (const { name } of documents) {
          await pageWorkspace.shareRecord(name, { recipient: created.username, permission: "READ" });
        }

        return { member: created, documents };
      });
    const memberSession = await flowDocumentSession(member);
    const memberDocuments = memberSession.documents;
    for (const document of documents) {
      expect((await memberDocuments.getById(document.id)).fields[0].content).toContain(content);
    }

    const sharedDocs = await test.step("When I open My RSpace > Shared Documents", async () => {
      await pageMyRSpace.open();
      return pageMyRSpace.navigateToSharedDocumentsPage();
    });

    await test.step("Then changing docX's permission to Edit persists after a reload, and docY is unaffected", async () => {
      await sharedDocs.setPermission(docX, member.fullName, "EDIT");
      expect(await sharedDocs.getPermission(docX, member.fullName)).toBe("EDIT");

      await sharedDocs.open();
      expect(await sharedDocs.getPermission(docX, member.fullName)).toBe("EDIT");
      expect(await sharedDocs.getPermission(docY, member.fullName)).toBe("READ");
    });

    await test.step("When I unshare docY, it disappears from Shared Documents but still exists in my workspace", async () => {
      await sharedDocs.unshare(docY, member.fullName);
      expect(await sharedDocs.isListed(docY, member.fullName)).toBe(false);
      await expectDocumentUnavailable(memberDocuments, documents[1].id);
      expect((await memberDocuments.getById(documents[0].id)).fields[0].content).toContain(content);
      expect((await clientDocuments.getById(documents[1].id)).fields[0].content).toContain(content);

      await pageWorkspace.searchFor(docY);
      await expect(pageWorkspace.table.row(docY)).toBeVisible();
    });
  });

  test("As a user, sharing individual notebook entries lets the recipient open each one via its global ID link", async ({
    flowRefreshDocumentSession,
    flowDocumentSession,
    appUser,
    pageWorkspace,
    clientSysadmin,
    clientFolders,
    clientDocuments,
  }) => {
    const notebookName = alphaNumericUnique("e2eShareEntriesNotebook");
    const sharedEntries = [alphaNumericUnique("e2eShareEntryOne"), alphaNumericUnique("e2eShareEntryTwo")];
    const unsharedEntry = alphaNumericUnique("e2eShareEntryUnshared");

    const recipient =
      await test.step("Given a group exists, and I share two notebook entries with a member individually at Read", async () => {
        const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eShareEntryMember", "ShareEntryMember");
        await clientSysadmin.createGroup({
          displayName: uniqueName("e2e-share-entries-group"),
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: user.username, roleInGroup: "DEFAULT" },
          ],
        });

        await flowRefreshDocumentSession();
        const notebook = await clientFolders.create({ name: notebookName, notebook: true });
        for (const name of [...sharedEntries, unsharedEntry]) {
          await clientDocuments.create({ name, parentFolderId: notebook.id });
        }
        for (const entry of sharedEntries) {
          await pageWorkspace.shareRecord(entry, { recipient: user.username, permission: "READ" });
        }

        return user;
      });

    const member = await flowDocumentSession(recipient);
    await test.step("Then the recipient sees only the shared entries under Shared with me", async () => {
      await member.workspace.open();
      await member.workspace.toolbar.toggleFilter("shared");
      for (const entry of sharedEntries) {
        await expect(member.workspace.table.row(entry)).toBeVisible();
      }
      await expect(member.workspace.table.row(unsharedEntry)).toHaveCount(0);
    });

    for (const entry of sharedEntries) {
      await test.step(`And the recipient can open "${entry}" directly via its global ID link, read-only`, async () => {
        await member.workspace.table.globalIdLink(entry).click();
        await member.document.isLoaded();
        expect(await member.document.header.getName()).toBe(entry);
        expect(await member.document.isReadOnly()).toBe(true);
        await member.workspace.open();
        await member.workspace.toolbar.toggleFilter("shared");
      });
    }
  });
});
