import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { CommunitiesPage } from "@/__tests__/e2e/pageObjects/system/communities/CommunitiesPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Communities", { tag: tags.SYSTEM }, () => {
  test("The default community can't be deleted, and creating one validates its inputs", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const admin = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCommunityAdmin");

    const { communities } = flowSysadminGroupAdmin;
    await communities.open();

    await communities.selectCommunity(CommunitiesPage.DEFAULT_COMMUNITY_NAME);
    const alert = await communities.attemptRemoveDefaultCommunity();
    await expect(alert.message).toContainText("You can't delete");
    await alert.confirm();

    const creation = await communities.newCommunity();
    await creation.submitButton.click();
    await expect(creation.errorText("Display name is a required field.")).toBeVisible();
    await expect(creation.errorText("Choosing an administrator is a required field.")).toBeVisible();

    const communityName = uniqueName("e2eCommunity");
    await creation.nameField.fill(communityName);
    await creation.adminCheckbox(admin.username).check();
    const list = await creation.submitExpectingSuccess();
    await expect(list.communityRow(communityName)).toBeVisible();

    await list.selectCommunity(communityName);
    await list.removeSelected();
  });

  test("A sysadmin can rename a community and move a Lab Group into it", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCommunityPi");
    const admin = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCommunityAdmin");
    const groupName = uniqueName("e2eCommunityGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [{ username: pi.username, roleInGroup: "PI" }],
    });

    const { communities } = flowSysadminGroupAdmin;
    await communities.open();
    const creation = await communities.newCommunity();
    const communityName = uniqueName("e2eCommunity");
    await creation.nameField.fill(communityName);
    await creation.adminCheckbox(admin.username).check();
    await creation.submitExpectingSuccess();

    await communities.open();
    const community = await communities.openCommunity(communityName);

    const renamedName = uniqueName("e2eCommunityRenamed");
    await community.rename(renamedName);
    expect(await community.getCommunityName()).toBe(renamedName);

    await community.addGroup(groupName);
    expect(await community.isGroupPresent(groupName)).toBe(true);

    await communities.deleteCommunity(renamedName);
  });

  test("A sysadmin can delete a non-default community", async ({ clientSysadmin, flowSysadminGroupAdmin }) => {
    const admin = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCommunityAdmin");

    const { communities } = flowSysadminGroupAdmin;
    await communities.open();
    const creation = await communities.newCommunity();
    const communityName = uniqueName("e2eCommunity");
    await creation.nameField.fill(communityName);
    await creation.adminCheckbox(admin.username).check();
    const list = await creation.submitExpectingSuccess();
    await expect(list.communityRow(communityName)).toBeVisible();

    await list.selectCommunity(communityName);
    await list.removeSelected();
    await expect(list.communityRow(communityName)).toHaveCount(0);
  });
});
