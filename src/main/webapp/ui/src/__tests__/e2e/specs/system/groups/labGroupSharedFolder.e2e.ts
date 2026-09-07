import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Lab Group shared folder", { tag: tags.SYSTEM }, () => {
  test("As a PI, I can create a subfolder inside the group's shared folder and move a member's shared document into it and back", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eSharedFolderPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSharedFolderMember");
    const groupName = uniqueName("e2eSharedFolderGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });
    const sharedFolderName = `${groupName}_SHARED`;

    const docName = uniqueName("e2eSharedFolderDoc");
    await new DocumentsClient(apiContext, member.apiKey).create({ name: docName });

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    await memberSession.workspace.searchBar.search(docName);
    await memberSession.workspace.table.selectRecord(docName);
    const shareDialog = await memberSession.workspace.selectionBar.share();
    await shareDialog.addRecipient(groupName);
    await shareDialog.setPermission(groupName, "READ");
    await shareDialog.save();

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.workspace.toolbar.clickLabGroupShortcut();
    await piSession.workspace.table.openRecord(sharedFolderName);
    await expect(piSession.workspace.table.row(docName)).toBeVisible();

    const subfolderName = uniqueName("e2eSharedFolderSubfolder");
    await piSession.workspace.createFolder(subfolderName);
    await expect(piSession.workspace.table.row(subfolderName)).toBeVisible();

    await piSession.workspace.table.selectRecord(docName);
    const moveIntoSubfolder = await piSession.workspace.selectionBar.move();
    await moveIntoSubfolder.clickFolder(sharedFolderName);
    await moveIntoSubfolder.clickFolder(subfolderName);
    await moveIntoSubfolder.clickMove();

    await expect(piSession.workspace.table.row(docName)).toHaveCount(0);
    await piSession.workspace.table.openRecord(subfolderName);
    await expect(piSession.workspace.table.row(docName)).toBeVisible();

    await piSession.workspace.table.selectRecord(docName);
    const moveBackToShared = await piSession.workspace.selectionBar.move();
    await moveBackToShared.clickFolder(sharedFolderName);
    await moveBackToShared.clickMove();

    await piSession.workspace.toolbar.clickLabGroupShortcut();
    await piSession.workspace.table.openRecord(sharedFolderName);
    await expect(piSession.workspace.table.row(docName)).toBeVisible();
  });
});
