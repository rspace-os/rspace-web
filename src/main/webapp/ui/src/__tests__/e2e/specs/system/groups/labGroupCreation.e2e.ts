import { expect } from "@playwright/test";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { createDynamicUser, type DynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { GroupAdminPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupAdminPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function createLabGroup(
  groupAdmin: GroupAdminPage,
  groupName: string,
  piUsername: string,
  memberUsername: string,
): Promise<void> {
  await groupAdmin.open();
  await groupAdmin.setName(groupName);
  await groupAdmin.selectGroupType("Lab Group");
  await groupAdmin.piPicker.addUser(piUsername);
  await groupAdmin.memberPicker.addUser(memberUsername);
  await groupAdmin.submit();
}

async function createLabGroupWithPiAndMember(
  clientSysadmin: SysadminClient,
  groupAdmin: GroupAdminPage,
  memberRole: "ROLE_PI" | "ROLE_USER" = "ROLE_USER",
): Promise<{ groupName: string; pi: DynamicUser; member: DynamicUser }> {
  const groupName = uniqueName("e2eLabGroup");
  const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eLabGroupPi");
  const member = await createDynamicUser(clientSysadmin, memberRole, "e2eLabGroupMember");

  await createLabGroup(groupAdmin, groupName, pi.username, member.username);

  return { groupName, pi, member };
}

test.describe("Lab Group creation", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can create a new Lab Group with a PI and a member", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupAdmin, groupDetails } = flowSysadminGroupAdmin;
    const { groupName, pi, member } = await createLabGroupWithPiAndMember(clientSysadmin, groupAdmin);

    await expect(groupDetails.heading).toHaveText(`Group: ${groupName}`);
    await expect(groupDetails.memberRow(pi.username)).toContainText("PI");
    await expect(groupDetails.memberRow(member.username)).toContainText("User");
  });

  test("As a sysadmin, I can see Join events in a new group's activity log", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupAdmin, groupDetails } = flowSysadminGroupAdmin;
    const { pi, member } = await createLabGroupWithPiAndMember(clientSysadmin, groupAdmin);

    await expect(groupDetails.activityRow(pi.fullName)).toContainText("Join");
    await expect(groupDetails.activityRow(member.fullName)).toContainText("Join");
  });

  test("As a sysadmin, I can rename a Lab Group", async ({ flowSysadminGroupAdmin, clientSysadmin }) => {
    const { groupAdmin, groupDetails } = flowSysadminGroupAdmin;
    await createLabGroupWithPiAndMember(clientSysadmin, groupAdmin);
    const renamedTo = uniqueName("e2eLabGroupRenamed");

    await groupDetails.rename(renamedTo);

    await expect(groupDetails.heading).toHaveText(`Group: ${renamedTo}`);
  });

  test("As a sysadmin, group listings update after swapping a Lab Group's PI", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupAdmin, groupDetails } = flowSysadminGroupAdmin;
    const { pi, member } = await createLabGroupWithPiAndMember(clientSysadmin, groupAdmin, "ROLE_PI");

    await groupDetails.changePi(member.fullName);

    await expect(groupDetails.memberRow(member.username)).toContainText("PI");
    await expect(groupDetails.memberRow(pi.username)).toContainText("User");
  });

  test("As a sysadmin, the same PI can belong to multiple Lab Groups", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupAdmin, users } = flowSysadminGroupAdmin;
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eLabGroupPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eLabGroupMember");

    await createLabGroup(groupAdmin, uniqueName("e2eLabGroupA"), pi.username, member.username);
    await createLabGroup(groupAdmin, uniqueName("e2eLabGroupB"), pi.username, member.username);

    await users.open();
    await users.search(pi.username);
    await expect(users.userRow(pi.username)).toContainText("2 groups");
  });

  test("As a PI, I can create and delete my own Lab Group", async ({ flowSelfServicePi }) => {
    const { selfServiceLabGroup, groupDetails } = await flowSelfServicePi();
    const groupName = uniqueName("e2eSelfServiceGroup");

    await selfServiceLabGroup.open();
    await selfServiceLabGroup.createGroup(groupName);
    await expect(groupDetails.heading).toHaveText(`Group: ${groupName}`);

    await groupDetails.deleteGroup();
  });

  test("As a non-creator PI, I cannot delete another PI's Lab Group", async ({ flowSelfServicePi }) => {
    const owner = await flowSelfServicePi();
    await owner.selfServiceLabGroup.open();
    const groupId = await owner.selfServiceLabGroup.createGroup(uniqueName("e2eOwnerOnlyGroup"));

    // An unrelated PI — not a member, not the owner — viewing the same group directly.
    const outsider = await flowSelfServicePi();
    await outsider.groupDetails.openGroup(groupId);

    await expect(outsider.groupDetails.heading).toBeVisible();
    await expect(outsider.groupDetails.deleteGroupButton).toHaveCount(0);
  });
});
