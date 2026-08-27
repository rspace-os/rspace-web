import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Project Group creation", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can create a Project Group with a Group Owner", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupAdmin, groupDetails } = flowSysadminGroupAdmin;
    const groupName = uniqueName("e2eProjectGroup");
    const owner = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eProjectGroupOwner");

    await groupAdmin.open();
    await groupAdmin.setName(groupName);
    await groupAdmin.selectGroupType("Project Group");
    await groupAdmin.ownerPicker.addUser(owner.username);
    await groupAdmin.submit();

    await expect(groupDetails.heading).toHaveText(`Project Group: ${groupName}`);
    await expect(groupDetails.memberRow(owner.username)).toContainText("Group Owner");
  });

  test("As a user, I can create my own Project Group and invite a member who must accept", async ({
    flowSelfServicePi,
  }) => {
    const owner = await flowSelfServicePi();
    const invitee = await flowSelfServicePi();
    const groupName = uniqueName("e2eSelfServiceProjectGroup");

    await owner.projectGroup.open();
    const groupId = await owner.projectGroup.createGroup(groupName);
    await expect(owner.groupDetails.heading).toHaveText(`Project Group: ${groupName}`);

    await owner.groupDetails.inviteMember(invitee.username);

    await invitee.workspace.open();
    const messages = await invitee.workspace.openReceivedMessages();
    await messages.acceptFirstRequest();
    await messages.close();

    await invitee.groupDetails.openGroup(groupId);
    await expect(invitee.groupDetails.memberRow(invitee.username)).toContainText("User");
  });

  test("As a non-owner, I cannot delete another user's Project Group", async ({ flowSelfServicePi }) => {
    const owner = await flowSelfServicePi();
    await owner.projectGroup.open();
    const groupId = await owner.projectGroup.createGroup(uniqueName("e2eOwnerOnlyProjectGroup"));

    const outsider = await flowSelfServicePi();
    await outsider.groupDetails.openGroup(groupId);

    await expect(outsider.groupDetails.heading).toBeVisible();
    await expect(outsider.groupDetails.deleteGroupButton).toHaveCount(0);
  });
});
