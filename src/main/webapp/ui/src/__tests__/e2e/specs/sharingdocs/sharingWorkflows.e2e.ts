import { expect } from "@playwright/test";
import { ApiError } from "@/__tests__/e2e/api/clients/BaseApiClient";
import { expectDocumentUnavailable } from "@/__tests__/e2e/assertions/documents";
import type { SelectionBarAction } from "@/__tests__/e2e/components/workspace/WorkspaceSelectionBar";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Sharing workflows and permissions", () => {
  test("As a group member with read-only access to a colleague's notebook, I can read its entries but cannot create one", async ({
    appUser,
    clientSysadmin,
    flowDocumentSession,
  }) => {
    const ownerUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eReadNotebookOwner");
    const readerUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eReadNotebookReader");
    const groupName = uniqueName("ReadNotebookGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: appUser.username, roleInGroup: "PI" },
        { username: ownerUser.username, roleInGroup: "DEFAULT" },
        { username: readerUser.username, roleInGroup: "DEFAULT" },
      ],
    });
    const owner = await flowDocumentSession(ownerUser);
    const notebook = await owner.folders.create({ name: alphaNumericUnique("ReadNotebook"), notebook: true });
    const entryContent = alphaNumericUnique("ReadNotebookEntry");
    await owner.documents.create({ name: "Entry 1", parentFolderId: notebook.id, fields: [{ content: entryContent }] });
    await owner.workspace.shareRecord(notebook.name, { recipient: groupName, permission: "READ" });

    await owner.workspace.table.openNotebook(notebook.name);
    await owner.notebook.isLoaded();
    await expect(owner.notebook.toolbar.createMenu.createButton).toBeVisible();

    const reader = await flowDocumentSession(readerUser);
    await reader.workspace.searchBar.search(notebook.name);
    await reader.workspace.table.openNotebook(notebook.name);
    await reader.notebook.isLoaded();
    await expect(reader.notebook.entryContent).toContainText(entryContent);
    await expect(reader.notebook.toolbar.createMenu.createButton).toBeHidden();

    const rejected = await reader.documents
      .create({ name: "Unauthorised entry", parentFolderId: notebook.id, fields: [{ content: "x" }] })
      .then(
        () => undefined,
        (error: unknown) => error,
      );
    expect(rejected, "Creating an entry in a read-only notebook must be refused").toBeInstanceOf(ApiError);
  });

  test("As a lab admin, group sharing grants edit access and a downgrade revokes editing", async ({
    appUser,
    clientSysadmin,
    flowDocumentSession,
  }) => {
    const ownerUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eAdminOwner");
    const adminUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSharingAdmin");
    const groupName = uniqueName("AdminSharingGroup");
    const group = await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: appUser.username, roleInGroup: "PI" },
        { username: ownerUser.username, roleInGroup: "DEFAULT" },
        { username: adminUser.username, roleInGroup: "DEFAULT" },
      ],
    });
    const pi = await flowDocumentSession(appUser);
    await pi.refreshAfterGroupChange();
    await pi.groupDetails.openGroup(group.id);
    await pi.groupDetails.makeMemberLabAdmin(adminUser.username, false);
    const owner = await flowDocumentSession(ownerUser);
    const documents = owner.documents;
    const original = alphaNumericUnique("OriginalContent");
    const edited = alphaNumericUnique("AdminEdit");
    const doc = await documents.create({
      name: alphaNumericUnique("LabAdminSharedDoc"),
      fields: [{ content: original }],
    });
    await owner.workspace.shareRecord(doc.name, { recipient: groupName, permission: "EDIT" });

    const admin = await flowDocumentSession(adminUser);
    const sharedFolderName = `${groupName}_SHARED`;
    await admin.workspace.searchBar.search(sharedFolderName);
    await admin.workspace.table.openRecord(sharedFolderName);
    await admin.workspace.waitUntilBreadcrumbShows(sharedFolderName);
    await admin.workspace.table.openRecord(doc.name);
    const viewed = admin.document;
    await viewed.isLoaded();
    expect(await viewed.isReadOnly()).toBe(false);
    const field = await viewed.editField("", 0);
    const saveActions = await admin.editor.editToolbar.availableSaveActions();
    expect(saveActions).toContain("Save");
    expect(saveActions).toContain("Save & Close");
    expect(saveActions).not.toContain("Save & New");
    expect(saveActions).not.toContain("Save & Clone");
    await field.fill(edited);
    await field.saveAndFinishEditing();
    expect((await documents.getById(doc.id)).fields[0].content).toContain(edited);
    await viewed.close();
    await admin.workspace.waitUntilBreadcrumbShows(sharedFolderName);
    await expect(admin.workspace.table.row(doc.name)).toBeVisible();
    await admin.workspace.table.openRecord(doc.name);
    await viewed.isLoaded();

    const shares = owner.sharedDocuments;
    await shares.open();
    await shares.setPermission(doc.name, groupName, "READ");
    await viewed.reload();
    expect(await viewed.isReadOnly()).toBe(true);
    await expect(await viewed.getFieldViewContent("", 0)).toContainText(edited);
  });

  test("As a lab admin, removing a member revokes the group share but preserves their individual share", async ({
    appUser,
    clientSysadmin,
    flowDocumentSession,
  }) => {
    const ownerUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRemovedOwner");
    const adminUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRemovingAdmin");
    const recipient = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRemainingRecipient");
    const groupName = uniqueName("RemoveMemberSharingGroup");
    const group = await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: appUser.username, roleInGroup: "PI" },
        ...[ownerUser, adminUser, recipient].map(({ username }) => ({ username, roleInGroup: "DEFAULT" as const })),
      ],
    });
    const pi = await flowDocumentSession(appUser);
    await pi.refreshAfterGroupChange();
    await pi.groupDetails.openGroup(group.id);
    await pi.groupDetails.makeMemberLabAdmin(adminUser.username, false);
    const content = alphaNumericUnique("PreservedMemberContent");
    const owner = await flowDocumentSession(ownerUser);
    const documents = owner.documents;
    const doc = await documents.create({ name: alphaNumericUnique("MemberRemovalDoc"), fields: [{ content }] });
    await owner.workspace.shareRecord(doc.name, { recipient: groupName, permission: "EDIT" });
    await owner.workspace.shareRecord(doc.name, { recipient: recipient.username, permission: "READ" });

    const admin = await flowDocumentSession(adminUser);
    const sharedBeforeRemoval = await admin.workspace.openDocument(doc.id);
    expect(await sharedBeforeRemoval.isReadOnly()).toBe(false);
    await expect(await sharedBeforeRemoval.getFieldViewContent("", 0)).toContainText(content);
    await admin.groupDetails.openGroup(group.id);
    await admin.groupDetails.removeMember(ownerUser.username);

    const shares = owner.sharedDocuments;
    await shares.open();
    await expect(shares.row(doc.name, recipient.fullName)).toBeVisible();
    await expect(shares.row(doc.name, groupName)).toHaveCount(0);
    expect((await documents.getById(doc.id)).fields[0].content).toContain(content);
    const reader = await flowDocumentSession(recipient);
    const viewed = await reader.workspace.openDocument(doc.id);
    expect(await viewed.isReadOnly()).toBe(true);
    await expect(await viewed.getFieldViewContent("", 0)).toContainText(content);
    await admin.workspace.searchFor(doc.name);
    await expect(admin.workspace.table.row(doc.name)).toHaveCount(0);
  });

  test("As a notebook owner, I can reshare contributed entries without transferring ownership", async ({
    appUser,
    clientSysadmin,
    clientFolders,
    clientDocuments,
    flowDocumentSession,
  }) => {
    // Three actors exercise two group shares, editing, and removal from a shared notebook.
    test.setTimeout(120_000);
    const contributor = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eNotebookContributor");
    const secondReader = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eNotebookSecondGroup");
    const firstGroup = uniqueName("ContributorGroup");
    const secondGroup = uniqueName("ResharedGroup");
    for (const [displayName, username] of [
      [firstGroup, contributor.username],
      [secondGroup, secondReader.username],
    ]) {
      await clientSysadmin.createGroup({
        displayName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username, roleInGroup: "DEFAULT" },
        ],
      });
    }
    const notebook = await clientFolders.create({ name: alphaNumericUnique("DestinationNotebook"), notebook: true });
    const piEntry = await clientDocuments.create({ name: alphaNumericUnique("PiEntry"), parentFolderId: notebook.id });
    const author = await flowDocumentSession(contributor);
    const contributorDocuments = author.documents;
    const sourceNotebook = await author.folders.create({
      name: alphaNumericUnique("SourceNotebook"),
      notebook: true,
    });
    const content = alphaNumericUnique("ContributedContent");
    const standalone = await contributorDocuments.create({
      name: alphaNumericUnique("ContributedDocument"),
      fields: [{ content }],
    });
    const entry = await contributorDocuments.create({
      name: alphaNumericUnique("ContributedEntry"),
      parentFolderId: sourceNotebook.id,
      fields: [{ content }],
    });
    const pi = await flowDocumentSession(appUser);
    await pi.refreshAfterGroupChange();
    await pi.workspace.shareRecord(notebook.name, { recipient: firstGroup, permission: "EDIT" });
    for (const record of [standalone, entry]) {
      await author.workspace.shareRecord(record.name, {
        recipient: firstGroup,
        permission: "EDIT",
        location: [`${firstGroup}_SHARED`, notebook.name],
      });
    }
    await pi.workspace.open(notebook.id);
    for (const record of [piEntry, standalone, entry]) await expect(pi.workspace.table.row(record.name)).toBeVisible();
    await pi.workspace.table.selectRecord(standalone.name);
    await pi.workspace.selectionBar.delete();
    await expect(pi.workspace.table.row(standalone.name)).toHaveCount(0);
    expect((await contributorDocuments.getById(standalone.id)).fields[0].content).toContain(content);
    // The document API picks an arbitrary non-shared-folder parent when there
    // are two notebooks. Verify the actual source membership and owner instead.
    await author.workspace.open(sourceNotebook.id);
    await expect(author.workspace.table.row(entry.name)).toBeVisible();
    expect(await contributorDocuments.getById(entry.id)).toMatchObject({ owner: { username: contributor.username } });

    await pi.workspace.shareRecord(notebook.name, { recipient: secondGroup, permission: "EDIT" });
    const reader = await flowDocumentSession(secondReader);
    await reader.workspace.open(notebook.id);
    await expect(reader.workspace.table.row(standalone.name)).toHaveCount(0);
    await expect(reader.workspace.table.row(piEntry.name)).toBeVisible();
    await expect(reader.workspace.table.row(entry.name)).toBeVisible();
    const viewed = await reader.workspace.openDocument(entry.id);
    await expect(await viewed.getFieldViewContent("", 0)).toContainText(content);
    const edited = alphaNumericUnique("SecondGroupEdit");
    const field = await viewed.editField("", 0);
    await field.fill(edited);
    await field.saveAndFinishEditing();
    expect((await contributorDocuments.getById(entry.id)).fields[0].content).toContain(edited);
    await author.workspace.open(sourceNotebook.id);
    await expect(author.workspace.table.row(entry.name)).toBeVisible();

    await pi.workspace.open(notebook.id);
    await pi.workspace.table.selectRecord(entry.name);
    await pi.workspace.selectionBar.delete();
    await expect(pi.workspace.table.row(entry.name)).toHaveCount(0);
    await reader.workspace.open(notebook.id);
    await expect(reader.workspace.table.row(entry.name)).toHaveCount(0);
    await expect(reader.workspace.table.row(piEntry.name)).toBeVisible();
    await author.workspace.open(sourceNotebook.id);
    await expect(author.workspace.table.row(entry.name)).toBeVisible();
    const preservedEntry = await contributorDocuments.getById(entry.id);
    expect(preservedEntry.parentFolderId).toBe(sourceNotebook.id);
    expect(preservedEntry.fields[0].content).toContain(edited);
  });

  test("As a recipient, I can create and share my own document from a shared template", async ({
    appUser,
    clientSysadmin,
    clientDocuments,
    flowDocumentSession,
  }) => {
    const recipient = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eTemplateRecipient");
    const reader = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eTemplateDocumentReader");
    const groupName = uniqueName("TemplateSharingGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: appUser.username, roleInGroup: "PI" },
        { username: recipient.username, roleInGroup: "DEFAULT" },
        { username: reader.username, roleInGroup: "DEFAULT" },
      ],
    });
    const content = alphaNumericUnique("SharedTemplateContent");
    const source = await clientDocuments.create({ name: alphaNumericUnique("TemplateSource"), fields: [{ content }] });
    const templateName = alphaNumericUnique("SharedTemplate");
    const owner = await flowDocumentSession(appUser);
    await owner.refreshAfterGroupChange();
    const doc = await owner.workspace.openDocument(source.id);
    await doc.saveAsTemplate(templateName);
    await owner.workspace.shareRecord(templateName, { recipient: recipient.username, permission: "READ" });

    const recipientSession = await flowDocumentSession(recipient);
    await recipientSession.workspace.open();
    const createdName = alphaNumericUnique("FromSharedTemplate");
    const created = await recipientSession.workspace.createDocumentFromSharedTemplate(templateName, createdName);
    expect(await created.header.getName()).toBe(createdName);
    const createdId = created.getId();
    const field = await created.getField("", 0);
    expect(await field.getText()).toContain(content);
    const recipientContent = alphaNumericUnique("RecipientTemplateEdit");
    await field.fill(recipientContent);
    await field.saveAndFinishEditing();
    const readerSession = await flowDocumentSession(reader);
    const readerDocuments = readerSession.documents;
    await expectDocumentUnavailable(readerDocuments, createdId);
    await recipientSession.workspace.shareRecord(createdName, { recipient: reader.username, permission: "READ" });
    const sharedCopy = await readerSession.workspace.openDocument(createdId);
    expect(await sharedCopy.isReadOnly()).toBe(true);
    await expect(await sharedCopy.getFieldViewContent("", 0)).toContainText(recipientContent);
    const persistedSharedCopy = await readerDocuments.getById(createdId);
    expect(persistedSharedCopy).toMatchObject({ owner: { username: recipient.username } });
    expect(persistedSharedCopy.fields[0].content).toContain(recipientContent);
    expect((await clientDocuments.getById(source.id)).fields[0].content).toContain(content);

    await recipientSession.workspace.open();
    await recipientSession.workspace.toolbar.toggleFilter("templates");
    await recipientSession.workspace.searchBar.search(templateName);
    await recipientSession.workspace.table.selectRecord(templateName);
    expect(await recipientSession.workspace.selectionBar.isActionVisible("Create Document")).toBe(true);
    const contextCreatedName = alphaNumericUnique("SharedTemplateContextCreated");
    const contextCreated = await recipientSession.workspace.createDocumentFromSelectedTemplate(contextCreatedName);
    const contextCreatedId = contextCreated.getId();
    expect(await contextCreated.header.getName()).toBe(contextCreatedName);
    expect(await (await contextCreated.getField("", 0)).getText()).toContain(content);
    await contextCreated.editToolbar.saveAndClose();
    const persistedCopy = await recipientSession.workspace.openDocument(contextCreatedId);
    await expect(await persistedCopy.getFieldViewContent("", 0)).toContainText(content);
  });

  test("As a template owner, duplication preserves template capabilities and creating from its context action preserves content", async ({
    clientDocuments,
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
  }) => {
    const content = alphaNumericUnique("TemplateContextContent");
    const source = await clientDocuments.create({
      name: alphaNumericUnique("TemplateContextSource"),
      fields: [{ content }],
    });
    const templateName = alphaNumericUnique("ContextTemplate");
    const sourcePage = await pageWorkspace.openDocument(source.id);
    await sourcePage.saveAsTemplate(templateName);
    await pageWorkspace.searchFor(templateName);
    await pageWorkspace.table.selectRecord(templateName);
    expect(await pageWorkspace.selectionBar.isActionVisible("Create Document")).toBe(true);
    await pageWorkspace.selectionBar.duplicate();
    const copiedName = `${templateName}_Copy`;
    await pageWorkspace.searchFor(copiedName);
    await expect(pageWorkspace.table.row(copiedName).getByTitle("Template", { exact: true })).toBeVisible();
    await pageWorkspace.table.selectRecord(copiedName);
    const createdName = alphaNumericUnique("ContextCreatedDocument");
    await pageWorkspace.selectionBar.createDocumentFromTemplate(createdName);
    await pageDocumentEditor.isLoaded();
    expect(await pageDocument.header.getName()).toBe(createdName);
    expect(await (await pageDocumentEditor.getField("", 0)).getText()).toContain(content);
    await pageDocumentEditor.editToolbar.saveAndClose();
    await pageWorkspace.searchFor(createdName);
    await pageWorkspace.table.openRecord(createdName);
    await pageDocument.isLoaded();
    await expect(await pageDocument.getFieldViewContent("", 0)).toContainText(content);
  });

  test("As a user, creating from a template inside a nested folder preserves the selected destination and content", async ({
    clientDocuments,
    clientFolders,
    pageWorkspace,
  }) => {
    const content = alphaNumericUnique("NestedTemplateContent");
    const source = await clientDocuments.create({
      name: alphaNumericUnique("NestedTemplateSource"),
      fields: [{ content }],
    });
    const templateName = alphaNumericUnique("NestedTemplate");
    const sourcePage = await pageWorkspace.openDocument(source.id);
    await sourcePage.saveAsTemplate(templateName);
    const parent = await clientFolders.create({ name: alphaNumericUnique("TemplateDestinationParent") });
    const child = await clientFolders.create({
      name: alphaNumericUnique("TemplateDestinationChild"),
      parentFolderId: parent.id,
    });

    await pageWorkspace.open(child.id);
    const createdName = alphaNumericUnique("NestedTemplateDocument");
    const created = await pageWorkspace.createDocumentFromTemplate(templateName, createdName);
    const createdId = created.getId();
    expect(await (await created.getField("", 0)).getText()).toContain(content);
    await created.editToolbar.saveAndClose();

    await pageWorkspace.open(child.id);
    await expect(pageWorkspace.table.row(createdName)).toBeVisible();
    const persisted = await clientDocuments.getById(createdId);
    expect(persisted.name).toBe(createdName);
    expect(persisted.parentFolderId).toBe(child.id);
    expect(persisted.fields[0].content).toContain(content);
    await pageWorkspace.open(parent.id);
    await expect(pageWorkspace.table.row(child.name)).toBeVisible();
    await expect(pageWorkspace.table.row(createdName)).toHaveCount(0);
  });

  test("As a user, my Templates root is protected while its subfolders and ordinary similarly named folders retain their own actions", async ({
    flowRefreshDocumentSession,
    appUser,
    clientSysadmin,
    clientDocuments,
    clientFolders,
    pageWorkspace,
  }) => {
    await clientSysadmin.createGroup({
      displayName: alphaNumericUnique("TemplateActionsGroup"),
      type: "LAB_GROUP",
      users: [{ username: appUser.username, roleInGroup: "PI" }],
    });
    await flowRefreshDocumentSession();
    const source = await clientDocuments.create({ name: alphaNumericUnique("ProtectedTemplatesSource") });
    const sourcePage = await pageWorkspace.openDocument(source.id);
    await sourcePage.saveAsTemplate(alphaNumericUnique("ProtectedTemplate"));

    await pageWorkspace.searchFor("Templates");
    await pageWorkspace.table.selectRecord("Templates");
    const supportedRootActions: SelectionBarAction[] = ["Export", "Add to Favorites", "Add/Remove Tags"];
    for (const action of supportedRootActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Templates root supports ${action}`).toBe(true);
    }
    const forbiddenRootActions: SelectionBarAction[] = [
      "Duplicate",
      "Move",
      "Rename",
      "Delete",
      "Create Document",
      "Share",
      "Revisions",
    ];
    for (const action of forbiddenRootActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Templates root prohibits ${action}`).toBe(
        false,
      );
    }

    await pageWorkspace.table.deselectRecord("Templates");
    await pageWorkspace.table.openRecord("Templates");
    await pageWorkspace.waitUntilBreadcrumbShows("Templates");
    await pageWorkspace.toolbar.createMenu.open();
    await expect(pageWorkspace.toolbar.createMenu.availableActions).toHaveText(["Folder"]);
    await pageWorkspace.toolbar.createMenu.close();
    const childName = alphaNumericUnique("TemplateSubfolder");
    await pageWorkspace.createFolder(childName);
    await pageWorkspace.table.selectRecord(childName);
    const supportedFolderActions: SelectionBarAction[] = [
      "Duplicate",
      "Move",
      "Rename",
      "Delete",
      "Export",
      "Add to Favorites",
      "Add/Remove Tags",
    ];
    const forbiddenFolderActions: SelectionBarAction[] = ["Create Document", "Share", "Revisions"];
    for (const action of supportedFolderActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Template subfolder supports ${action}`).toBe(
        true,
      );
    }
    for (const action of forbiddenFolderActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Template subfolder prohibits ${action}`).toBe(
        false,
      );
    }
    const move = await pageWorkspace.selectionBar.move();
    await expect(move.folder("Templates")).toBeVisible();
    await expect(move.folder("Home")).toHaveCount(0);
    await move.cancel();

    const ordinary = await clientFolders.create({ name: alphaNumericUnique("Templates") });
    await pageWorkspace.searchFor(ordinary.name);
    await pageWorkspace.table.selectRecord(ordinary.name);
    for (const action of supportedFolderActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Ordinary folder supports ${action}`).toBe(true);
    }
    for (const action of forbiddenFolderActions) {
      expect(await pageWorkspace.selectionBar.isActionVisible(action), `Ordinary folder prohibits ${action}`).toBe(
        false,
      );
    }
  });
});
