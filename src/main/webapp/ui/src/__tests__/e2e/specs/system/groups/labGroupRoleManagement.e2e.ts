import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Lab Group role management", { tag: tags.SYSTEM }, () => {
  test("A Lab Admin who can view all documents sees other members' documents in search", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
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
    const docName = uniqueName("e2eRoleMgmtViewAllDoc");
    await new DocumentsClient(apiContext, owner.apiKey).create({ name: docName });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, true);

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toBeVisible();
  });

  test("A Lab Admin without view-all permission cannot see other members' documents in search", async ({
    clientSysadmin,
    apiContext,
    flowUserSession,
  }) => {
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
    const docName = uniqueName("e2eRoleMgmtNoViewAllDoc");
    await new DocumentsClient(apiContext, owner.apiKey).create({ name: docName });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.makeMemberLabAdmin(labAdmin.username, false);

    const labAdminSession = await flowUserSession(labAdmin.username, DYNAMIC_USER_PASSWORD);
    await labAdminSession.workspace.searchBar.search(docName);
    await expect(labAdminSession.workspace.table.row(docName)).toHaveCount(0);
  });

  test("Demoting a Lab Admin back to User is reflected in the members table", async ({
    clientSysadmin,
    flowUserSession,
  }) => {
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
});
