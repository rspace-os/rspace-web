import { expect } from "@playwright/test";
import { expectDocumentUnavailable } from "@/__tests__/e2e/assertions/documents";
import { test } from "@/__tests__/e2e/fixtures/flows/sharingGroups";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.describe("Deleting and moving shared records", () => {
  test.describe.configure({ timeout: 120_000 });
  for (const sharing of ["individual", "lab", "collaboration"] as const) {
    test(`As an owner, deleting a ${sharing}-shared document revokes access and removes its share records`, async ({
      flowSharingGroup,
      clientDocuments,
    }) => {
      const group = await flowSharingGroup(sharing === "collaboration" ? "collaboration" : "lab");
      const groupShare = sharing !== "individual";
      const recipient = groupShare ? group.name : group.recipientUsername;
      const recipientLabel = groupShare ? group.name : group.recipientName;
      const document = await clientDocuments.create({
        name: alphaNumericUnique("SharedDelete"),
        fields: [{ content: "Recipient can read this before owner deletion" }],
      });
      await group.owner.workspace.shareRecord(document.name, { recipient: recipient, permission: "EDIT" });
      await group.recipient.workspace.searchFor(document.name);
      await expect(group.recipient.workspace.table.row(document.name)).toBeVisible();
      expect((await group.recipient.documents.getById(document.id)).fields[0].content).toBe(document.fields[0].content);
      const management = group.owner.sharedDocuments;
      await management.open();
      await expect(management.row(document.name, recipientLabel)).toBeVisible();

      if (groupShare) {
        for (const actor of [group.owner, group.recipient]) {
          await actor.workspace.openSharedFolder(group);
          await expect(actor.workspace.table.row(document.name)).toBeVisible();
        }
      }

      await group.owner.workspace.searchFor(document.name);
      await group.owner.workspace.table.selectRecord(document.name);
      await group.owner.workspace.selectionBar.delete();

      await management.open();
      await expect(management.row(document.name, recipientLabel)).toHaveCount(0);
      for (const actor of [group.owner, group.recipient]) {
        await actor.workspace.searchFor(document.name);
        await expect(actor.workspace.table.row(document.name)).toHaveCount(0);
        if (groupShare) {
          await actor.workspace.openSharedFolder(group);
          await expect(actor.workspace.table.row(document.name)).toHaveCount(0);
        }
      }
      await expectDocumentUnavailable(group.recipient.documents, document.id);
    });
  }

  test("As a collaboration PI, deleting a shared parent folder unshares nested notebooks while preserving originals and sibling shares", async ({
    flowSharingGroup,
    clientFolders,
    clientDocuments,
  }) => {
    const group = await flowSharingGroup("collaboration");
    const parent = alphaNumericUnique("SharedParent");
    const nested = alphaNumericUnique("SharedNested");
    const sibling = alphaNumericUnique("SharedSibling");
    await group.owner.workspace.openSharedFolder(group);
    await group.owner.workspace.createFolder(parent, { navigate: true });
    await group.owner.workspace.createFolder(nested, { navigate: true });
    await group.owner.workspace.openSharedFolder(group);
    await group.owner.workspace.createFolder(sibling, { navigate: true });

    const notebook = await clientFolders.create({ name: alphaNumericUnique("NestedNotebook"), notebook: true });
    const content = alphaNumericUnique("NestedEntryContent");
    const entry = await clientDocuments.create({
      name: alphaNumericUnique("NestedEntry"),
      parentFolderId: notebook.id,
      fields: [{ content }],
    });
    const siblingDocument = await clientDocuments.create({ name: alphaNumericUnique("SiblingDocument") });
    await group.owner.workspace.shareRecord(notebook.name, {
      recipient: group.name,
      permission: "EDIT",
      location: [`${group.name}_SHARED`, parent, nested],
    });
    await group.owner.workspace.shareRecord(siblingDocument.name, {
      recipient: group.name,
      permission: "EDIT",
      location: [`${group.name}_SHARED`, sibling],
    });
    await group.recipient.workspace.searchFor(notebook.name);
    await expect(group.recipient.workspace.table.row(notebook.name)).toBeVisible();
    expect((await group.recipient.documents.getById(entry.id)).fields[0].content).toBe(content);

    await group.recipient.workspace.openSharedFolder(group);
    await group.recipient.workspace.table.selectRecord(parent);
    await group.recipient.workspace.selectionBar.delete();

    const management = group.owner.sharedDocuments;
    await management.open();
    await expect(management.row(notebook.name, group.name)).toHaveCount(0);
    await expect(management.row(siblingDocument.name, group.name)).toBeVisible();
    await group.recipient.workspace.searchFor(notebook.name);
    await expect(group.recipient.workspace.table.row(notebook.name)).toHaveCount(0);
    await expectDocumentUnavailable(group.recipient.documents, entry.id);
    await group.recipient.workspace.searchFor(siblingDocument.name);
    await expect(group.recipient.workspace.table.row(siblingDocument.name)).toBeVisible();
    await group.owner.workspace.open(notebook.id);
    await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();
    const original = await clientDocuments.getById(entry.id);
    expect(original.parentFolderId).toBe(notebook.id);
    expect(original.fields[0].content).toBe(content);
  });

  test("As an owner, notebook and entry share cycles and a lab administrator's deletion preserve my entry", async ({
    flowSharingGroup,
    clientFolders,
    clientDocuments,
  }) => {
    const group = await flowSharingGroup();
    const notebook = await clientFolders.create({ name: alphaNumericUnique("ShareCycleNotebook"), notebook: true });
    const content = alphaNumericUnique("ShareCycleContent");
    const entry = await clientDocuments.create({
      name: alphaNumericUnique("ShareCycleEntry"),
      parentFolderId: notebook.id,
      fields: [{ content }],
    });
    await group.owner.workspace.shareRecord(entry.name, { recipient: group.name, permission: "EDIT" });
    await group.owner.workspace.shareRecord(notebook.name, { recipient: group.name, permission: "EDIT" });
    let management = group.owner.sharedDocuments;
    await management.open();
    await expect(management.row(entry.name, group.name)).toBeVisible();
    await expect(management.row(notebook.name, group.name)).toBeVisible();
    expect((await group.recipient.documents.getById(entry.id)).fields[0].content).toBe(content);

    await management.unshare(entry.name, group.name);
    await expect(management.row(entry.name, group.name)).toHaveCount(0);
    await expect(management.row(notebook.name, group.name)).toBeVisible();
    await group.owner.workspace.open(notebook.id);
    await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();
    expect((await clientDocuments.getById(entry.id)).fields[0].content).toBe(content);
    await group.recipient.workspace.open(notebook.id);
    await expect(group.recipient.workspace.table.row(entry.name)).toBeVisible();
    expect((await group.recipient.documents.getById(entry.id)).fields[0].content).toBe(content);

    await group.owner.workspace.shareRecord(entry.name, { recipient: group.name, permission: "EDIT" });
    management = group.owner.sharedDocuments;
    await management.open();
    await management.unshare(notebook.name, group.name);
    await expect(management.row(notebook.name, group.name)).toHaveCount(0);
    await expect(management.row(entry.name, group.name)).toHaveCount(0);
    await group.recipient.workspace.searchFor(entry.name);
    await expect(group.recipient.workspace.table.row(entry.name)).toHaveCount(0);
    await expectDocumentUnavailable(group.recipient.documents, entry.id);
    await group.owner.workspace.open(notebook.id);
    await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();

    await group.owner.workspace.shareRecord(entry.name, { recipient: group.name, permission: "EDIT" });
    await group.recipient.workspace.openSharedFolder(group);
    await group.recipient.workspace.table.selectRecord(entry.name);
    await group.recipient.workspace.selectionBar.delete();

    await group.owner.workspace.open(notebook.id);
    await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();
    const original = await clientDocuments.getById(entry.id);
    expect(original.parentFolderId).toBe(notebook.id);
    expect(original.fields[0].content).toBe(content);
    management = group.owner.sharedDocuments;
    await management.open();
    await expect(management.row(entry.name, group.name)).toHaveCount(0);
  });

  test("As a PI, Favorites preserves contributed entries when moving my notebook and rejects foreign records", async ({
    flowSharingGroup,
    clientDocuments,
    clientFolders,
    appUser,
  }) => {
    const group = await flowSharingGroup();
    const own = await clientDocuments.create({
      name: alphaNumericUnique("FavoriteOwn"),
      fields: [{ content: "Owned document content survives moving" }],
    });
    const ownNotebook = await clientFolders.create({
      name: alphaNumericUnique("FavoriteOwnNotebook"),
      notebook: true,
      parentFolderId: group.ownerHomeFolderId,
    });
    const ownEntry = await clientDocuments.create({
      name: alphaNumericUnique("FavoriteOwnEntry"),
      parentFolderId: ownNotebook.id,
      fields: [{ content: "My entry stays inside my moved notebook" }],
    });
    const foreignNotebook = await group.recipient.folders.create({
      name: alphaNumericUnique("FavoriteForeignNotebook"),
      notebook: true,
    });
    const foreign = await group.recipient.documents.create({
      name: alphaNumericUnique("FavoriteForeign"),
      parentFolderId: foreignNotebook.id,
      fields: [{ content: "Colleague document stays with its owner" }],
    });
    const contributed = await group.recipient.documents.create({
      name: alphaNumericUnique("FavoriteContributed"),
      fields: [{ content: "The colleague's contribution survives moving my notebook" }],
    });
    expect(contributed.parentFolderId).toEqual(expect.any(Number));
    const destination = await clientFolders.create({
      name: alphaNumericUnique("FavoriteDestination"),
      parentFolderId: group.ownerHomeFolderId,
    });
    await group.owner.workspace.shareRecord(own.name, { recipient: group.name, permission: "EDIT" });
    await group.owner.workspace.shareRecord(ownNotebook.name, { recipient: group.name, permission: "EDIT" });
    await group.recipient.workspace.shareRecord(foreignNotebook.name, { recipient: group.name, permission: "EDIT" });
    await group.recipient.workspace.shareRecord(contributed.name, {
      recipient: group.name,
      permission: "EDIT",
      location: [`${group.name}_SHARED`, ownNotebook.name],
    });
    await group.owner.workspace.open(ownNotebook.id);
    for (const entry of [ownEntry, contributed]) {
      await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();
    }
    const sharedFolder = `${group.name}_SHARED`;
    for (const name of [own.name, ownNotebook.name, foreign.name, sharedFolder]) {
      await group.owner.workspace.searchFor(name);
      await group.owner.workspace.table.selectRecord(name);
      await group.owner.workspace.selectionBar.toggleFavorite();
    }
    await group.owner.workspace.open();
    await group.owner.workspace.toolbar.toggleFilter("favorites");
    await group.owner.workspace.table.selectRecords(own.name, ownNotebook.name, foreign.name, sharedFolder);
    expect(await group.owner.workspace.selectionBar.isActionVisible("Move")).toBe(false);

    await group.owner.workspace.table.deselectRecord(foreign.name);
    const move = await group.owner.workspace.selectionBar.move();
    await move.clickFolder("Home");
    await move.clickFolder(destination.name);
    await move.clickMove();
    await expect(move.rejection.message).toContainText("Some records were not moved");
    await move.rejection.confirm();

    await group.owner.workspace.open(destination.id);
    await expect(group.owner.workspace.table.row(own.name)).toBeVisible();
    await expect(group.owner.workspace.table.row(ownNotebook.name)).toBeVisible();
    await expect(group.owner.workspace.table.row(foreign.name)).toHaveCount(0);
    await expect(group.owner.workspace.table.row(sharedFolder)).toHaveCount(0);
    const moved = await clientDocuments.getById(own.id);
    expect(moved).toMatchObject({ owner: { username: appUser.username } });
    expect(moved.parentFolderId).toBe(destination.id);
    expect(moved.fields[0].content).toBe(own.fields[0].content);
    const unmoved = await group.recipient.documents.getById(foreign.id);
    expect(unmoved.parentFolderId).toBe(foreignNotebook.id);
    expect(unmoved.fields[0].content).toBe(foreign.fields[0].content);
    expect(unmoved).toMatchObject({ owner: { username: group.recipientUsername } });

    await group.owner.workspace.table.openRecord(ownNotebook.name);
    await group.owner.workspace.waitUntilBreadcrumbShows(ownNotebook.name);
    for (const entry of [ownEntry, contributed]) {
      await expect(group.owner.workspace.table.row(entry.name)).toBeVisible();
    }
    const preservedOwnEntry = await clientDocuments.getById(ownEntry.id);
    expect(preservedOwnEntry).toMatchObject({
      parentFolderId: ownNotebook.id,
      owner: { username: appUser.username },
    });
    expect(preservedOwnEntry.fields[0].content).toBe(ownEntry.fields[0].content);
    const preservedContribution = await group.recipient.documents.getById(contributed.id);
    expect(preservedContribution).toMatchObject({
      owner: { username: group.recipientUsername },
    });
    expect(preservedContribution.fields[0].content).toBe(contributed.fields[0].content);

    await group.recipient.workspace.open(contributed.parentFolderId);
    await expect(group.recipient.workspace.table.row(contributed.name)).toBeVisible();
    await group.recipient.workspace.open(foreignNotebook.id);
    await expect(group.recipient.workspace.table.row(foreign.name)).toBeVisible();
    await group.owner.workspace.openSharedFolder(group);
    await expect(group.owner.workspace.table.row(foreignNotebook.name)).toBeVisible();
  });

  test("As a PI, moving a colleague's shared document into my notebook is rejected (RSDEV-897)", async ({
    flowSharingGroup,
    clientFolders,
  }) => {
    test.skip(true, "Known bug: RSDEV-897 — move out of a shared folder is never rejected on ownership grounds");

    const group = await flowSharingGroup();
    const notebook = await clientFolders.create({
      name: alphaNumericUnique("MoveTargetNotebook"),
      notebook: true,
      parentFolderId: group.ownerHomeFolderId,
    });
    const document = await group.recipient.documents.create({
      name: alphaNumericUnique("ForeignNotebookMove"),
      fields: [{ content: "Moving a share must not move the owner's original" }],
    });
    await group.recipient.workspace.shareRecord(document.name, { recipient: group.name, permission: "EDIT" });
    await group.owner.workspace.open();
    await group.owner.workspace.toolbar.toggleFilter("shared");
    await group.owner.workspace.table.selectRecord(document.name);
    const move = await group.owner.workspace.selectionBar.move();
    await move.clickFolder("Home");
    await move.clickFolder(notebook.name);
    await move.clickMove();
    // Keep checking persisted state when the expected rejection message is absent.
    await expect.soft(move.rejection.message).toContainText("Some records were not moved");
    const original = await group.recipient.documents.getById(document.id);
    expect(original.parentFolderId).toBe(document.parentFolderId);
    expect(original.fields[0].content).toBe(document.fields[0].content);
    await group.owner.workspace.open(notebook.id);
    await expect.soft(group.owner.workspace.table.row(document.name)).toHaveCount(0);
    await group.recipient.workspace.openSharedFolder(group);
    await expect(group.recipient.workspace.table.row(document.name)).toBeVisible();
  });
});
