import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

test.describe("Sysadmin Operate As", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can operate as another user and release it", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eOperateAsUser");

    const { users, workspace } = flowSysadminGroupAdmin;
    await users.open();
    const dialog = await users.clickOperateAs();
    await dialog.setUser(user.username);
    await dialog.submit(SYSADMIN.password);

    await workspace.waitUntilLoaded();
    await expect(workspace.operateAsBanner).toBeVisible();
    expect(await workspace.isOwnerVisible("E2E")).toBe(true);

    await workspace.releaseOperateAs();
    expect(await workspace.isOwnerVisible("E2E")).toBe(false);
  });

  test("Operate As rejects a wrong password or an unknown username", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eOperateAsReject");

    const { users } = flowSysadminGroupAdmin;
    await users.open();
    const dialog = await users.clickOperateAs();

    await dialog.setUser(user.username);
    await dialog.submit("wrong-password-entirely");
    await expect(dialog.errorMessage("Reauthentication failed")).toBeVisible();

    await dialog.userField.fill("not-a-real-username");
    await dialog.submit(SYSADMIN.password);
    await expect(dialog.errorMessage("invalid username")).toBeVisible();

    await dialog.cancel();
  });

  test("A community admin can't operate as another community's admin or as sysadmin", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
    flowUserSession,
  }) => {
    const ca1 = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCa1");
    const ca2 = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCa2");
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eCaPi");
    const groupName = uniqueName("e2eCaGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: ca2.username, roleInGroup: "DEFAULT" },
      ],
    });

    const { communities } = flowSysadminGroupAdmin;
    await communities.open();
    const creation = await communities.newCommunity();
    const communityName = uniqueName("e2eCaCommunity");
    await creation.nameField.fill(communityName);
    await creation.adminCheckbox(ca1.username).check();
    await creation.submitExpectingSuccess();

    await communities.open();
    const community = await communities.openCommunity(communityName);
    await community.addGroup(groupName);

    const ca1Session = await flowUserSession(ca1.username, DYNAMIC_USER_PASSWORD);
    await ca1Session.users.open();
    const dialog = await ca1Session.users.clickOperateAs();

    await dialog.setUser(ca2.username);
    await dialog.submit(DYNAMIC_USER_PASSWORD);
    await expect(dialog.errorMessage("authorised").or(dialog.errorMessage("currently logged"))).toBeVisible();

    await dialog.userField.fill(SYSADMIN.username);
    await dialog.submit(DYNAMIC_USER_PASSWORD);
    await expect(dialog.errorMessage("authorised").or(dialog.errorMessage("currently logged"))).toBeVisible();

    await dialog.cancel();

    await communities.deleteCommunity(communityName);
  });

  test("A sysadmin can operate as a user to reset their password", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
    flowUserSession,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eOperateAsPwReset");
    const newPassword = uniqueName("e2eNewPw");

    const { users, workspace, profile } = flowSysadminGroupAdmin;
    await users.open();
    const dialog = await users.clickOperateAs();
    await dialog.setUser(user.username);
    await dialog.submit(SYSADMIN.password);

    await workspace.waitUntilLoaded();
    expect(await workspace.isOwnerVisible("E2E")).toBe(true);

    await profile.open();
    await profile.waitUntilLoaded();
    const changePassword = await profile.openChangePassword();
    // Real, live-verified finding: while impersonating, the "current password" field accepts the
    // sysadmin's own login password, not the impersonated user's actual password — the backend
    // doesn't require knowledge of the target's password during Operate As. Confirmed live and
    // matches Java's `changePassword(getSysAdminPassword(), newPassword, newPassword)`.
    await changePassword.save(SYSADMIN.password, newPassword);
    await expect(changePassword.successMessage("Password changed successfully")).toBeVisible();

    // #runAs is injected site-wide and evaluate-clickable from any page, confirmed live — no need
    // to navigate back to /workspace first, releaseOperateAs() already waits for that redirect.
    await workspace.releaseOperateAs();

    const newSession = await flowUserSession(user.username, newPassword);
    expect(await newSession.workspace.isLoaded()).toBe(true);
  });
});
