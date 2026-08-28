import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import type { ApiSysadminGroup } from "@/__tests__/e2e/api/models/sysadmin";
import type { DynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

async function createLabGroupWithPiLabAdminAndOwner(
  clientSysadmin: SysadminClient,
): Promise<{ pi: DynamicUser; labAdmin: DynamicUser; owner: DynamicUser; group: ApiSysadminGroup }> {
  const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPi");
  const labAdmin = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtLabAdmin");
  const owner = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtOwner");
  const group = await clientSysadmin.createGroup({
    displayName: uniqueName("e2eRoleMgmtGroup"),
    type: "LAB_GROUP",
    users: [
      { username: pi.username, roleInGroup: "PI" },
      { username: labAdmin.username, roleInGroup: "DEFAULT" },
      { username: owner.username, roleInGroup: "DEFAULT" },
    ],
  });
  return { pi, labAdmin, owner, group };
}

async function createLabGroupWithPiAndMember(
  clientSysadmin: SysadminClient,
): Promise<{ pi: DynamicUser; member: DynamicUser; group: ApiSysadminGroup }> {
  const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPi");
  const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtMember");
  const group = await clientSysadmin.createGroup({
    displayName: uniqueName("e2eRoleMgmtGroup"),
    type: "LAB_GROUP",
    users: [
      { username: pi.username, roleInGroup: "PI" },
      { username: member.username, roleInGroup: "DEFAULT" },
    ],
  });
  return { pi, member, group };
}

test.describe("Lab Group role management", { tag: tags.SYSTEM }, () => {
  test("As a Lab Admin who can view all documents, I can see other members' documents in search", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
    const { pi, labAdmin, owner, group } = await createLabGroupWithPiLabAdminAndOwner(clientSysadmin);
    const docName = uniqueName("e2eRoleMgmtViewAllDoc");
    await new DocumentsClient(apiContext, owner.apiKey).create({ name: docName });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, true);

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toBeVisible();
  });

  test("As a Lab Admin without view-all permission, I cannot see other members' documents in search", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
    const { pi, labAdmin, owner, group } = await createLabGroupWithPiLabAdminAndOwner(clientSysadmin);
    const docName = uniqueName("e2eRoleMgmtNoViewAllDoc");
    await new DocumentsClient(apiContext, owner.apiKey).create({ name: docName });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, false);

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toHaveCount(0);
  });

  test("As a PI, I can demote a Lab Admin back to User and see it reflected in the members table", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const { pi, member, group } = await createLabGroupWithPiAndMember(clientSysadmin);

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(member.username, true);
    await expect(piSession.groupDetails.memberRow(member.username)).toContainText("Lab Admin");

    await piSession.groupDetails.makeMemberUser(member.username);
    await expect(piSession.groupDetails.memberRow(member.username)).toContainText("User");
  });

  test("As a plain member, I cannot manage another member's role", async ({ clientSysadmin, flowUserSession }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPi");
    const memberA = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtMemberA");
    const memberB = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtMemberB");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eRoleMgmtGroup"),
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: memberA.username, roleInGroup: "DEFAULT" },
        { username: memberB.username, roleInGroup: "DEFAULT" },
      ],
    });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await expect(piSession.groupDetails.memberChangeRoleButton(memberA.username)).toBeVisible();
    await expect(piSession.groupDetails.memberRemoveButton(memberA.username)).toBeVisible();

    const memberBSession = await flowUserSession(memberB.username, DYNAMIC_USER_PASSWORD);
    await memberBSession.groupDetails.openGroup(group.id);
    await expect(memberBSession.groupDetails.memberChangeRoleButton(memberA.username)).toHaveCount(0);
    await expect(memberBSession.groupDetails.memberRemoveButton(memberA.username)).toHaveCount(0);
  });

  test("As a Lab Admin with view-all, I can see a newly invited member once they accept, and lose sight of them once removed", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPi");
    const labAdmin = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtLabAdmin");
    const invitee = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtInvitee");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eRoleMgmtGroup"),
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: labAdmin.username, roleInGroup: "DEFAULT" },
      ],
    });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, true);
    await expect(piSession.groupDetails.memberRow(labAdmin.username)).toContainText("Lab Admin");
    await piSession.groupDetails.inviteMember(invitee.username);

    const inviteeSession = await flowUserSession(invitee.username, DYNAMIC_USER_PASSWORD);
    await inviteeSession.groupInvitation.accept();

    const docName = uniqueName("e2eRoleMgmtInviteeDoc");
    await new DocumentsClient(apiContext, invitee.apiKey).create({ name: docName });

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toBeVisible();
    await labAdminSession.groupDetails.openGroup(group.id);
    await expect(labAdminSession.groupDetails.memberRow(invitee.username)).toBeVisible();

    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.removeMember(invitee.username);

    await labAdminSession.groupDetails.openGroup(group.id);
    await expect(labAdminSession.groupDetails.memberRow(invitee.username)).toHaveCount(0);
    await labAdminSession.workspace.open();
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toHaveCount(0);
  });

  test("As a Lab Admin with view-all, I can browse a member's home folder but not the PI's, and I lose access once the member is removed", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const { pi, labAdmin, owner, group } = await createLabGroupWithPiLabAdminAndOwner(clientSysadmin);

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, true);
    await expect(piSession.groupDetails.memberRow(labAdmin.username)).toContainText("Lab Admin");

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.groupDetails.openGroup(group.id);
    await expect(labAdminSession.groupDetails.homeFolderLink(pi.username)).toHaveCount(0);

    const ownerHomeFolderHref = await labAdminSession.groupDetails.homeFolderLink(owner.username).getAttribute("href");
    const ownerHomeFolderId = Number((ownerHomeFolderHref ?? "").split("/workspace/")[1]);

    await labAdminSession.groupDetails.homeFolderLink(owner.username).click();
    await labAdminSession.workspace.waitUntilBreadcrumbShows(owner.username);

    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.removeMember(owner.username);

    await labAdminSession.workspace.open(ownerHomeFolderId);
    await expect(labAdminSession.workspace.breadcrumbFolderName.filter({ hasText: owner.username })).toHaveCount(0);
  });

  test("As a PI, I can see a plain member's home folder link but not a co-PI's, while a plain member sees none at all", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const piA = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPiA");
    const piB = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPiB");
    const other = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRoleMgmtOther");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eRoleMgmtGroup"),
      type: "LAB_GROUP",
      users: [
        { username: piA.username, roleInGroup: "PI" },
        { username: piB.username, roleInGroup: "DEFAULT" },
        { username: other.username, roleInGroup: "DEFAULT" },
      ],
    });

    const piASession = await flowUserSession(piA.username, DYNAMIC_USER_PASSWORD);
    await piASession.groupDetails.openGroup(group.id);
    await expect(piASession.groupDetails.homeFolderLink(piA.username)).toBeVisible();
    await expect(piASession.groupDetails.homeFolderLink(other.username)).toBeVisible();
    await expect(piASession.groupDetails.homeFolderLink(piB.username)).toHaveCount(0);

    const otherSession = await flowUserSession(other.username, DYNAMIC_USER_PASSWORD);
    await otherSession.groupDetails.openGroup(group.id);
    await expect(otherSession.groupDetails.homeFolderLink(piA.username)).toHaveCount(0);
    await expect(otherSession.groupDetails.homeFolderLink(other.username)).toHaveCount(0);
    await expect(otherSession.groupDetails.homeFolderLink(piB.username)).toHaveCount(0);
  });

  test("As a PI, I still can't see the other PI's home folder after switching to a Collaboration Group via Change Group", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const piALastName = uniqueName("e2eRoleMgmtPiA");
    const piBLastName = uniqueName("e2eRoleMgmtPiB");
    const piA = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPiA", piALastName);
    const piB = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eRoleMgmtPiB", piBLastName);
    await clientSysadmin.createGroup({
      displayName: uniqueName("e2eRoleMgmtPiBOwnGroup"),
      type: "LAB_GROUP",
      users: [{ username: piB.username, roleInGroup: "PI" }],
    });
    const labGroup = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eRoleMgmtGroup"),
      type: "LAB_GROUP",
      users: [{ username: piA.username, roleInGroup: "PI" }],
    });

    const piASession = await flowUserSession(piA.username, DYNAMIC_USER_PASSWORD);
    await piASession.groupDetails.openGroup(labGroup.id);
    await piASession.groupDetails.requestCollaborationGroup(piB.username);

    const piBSession = await flowUserSession(piB.username, DYNAMIC_USER_PASSWORD);
    const messages = await piBSession.workspace.openReceivedMessages();
    await messages.acceptFirstRequest();
    await messages.close();

    const collabGroupName = `${piALastName}-${piBLastName}-collabGroup`;
    await piASession.groupDetails.openGroup(labGroup.id);
    await piASession.groupDetails.changeGroup(collabGroupName);

    await expect(piASession.groupDetails.homeFolderLink(piA.username)).toBeVisible();
    await expect(piASession.groupDetails.homeFolderLink(piB.username)).toHaveCount(0);
  });

  test("As a PI or a plain member, I can see the group's community link and navigate to the Directory", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
    const { pi, member, group } = await createLabGroupWithPiAndMember(clientSysadmin);

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await expect(piSession.groupDetails.communityLink).toBeVisible();
    await piSession.groupDetails.communityLink.click();
    await expect(piSession.directory.heading).toContainText("Community");

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    await memberSession.groupDetails.openGroup(group.id);
    await expect(memberSession.groupDetails.communityLink).toBeVisible();
    await memberSession.groupDetails.communityLink.click();
    await expect(memberSession.directory.heading).toContainText("Community");
  });
});
