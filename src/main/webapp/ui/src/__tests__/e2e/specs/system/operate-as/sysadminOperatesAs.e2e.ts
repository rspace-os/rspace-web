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
    await expect(workspace.operateAsBanner).toContainText(user.username);
    expect(await workspace.isOwnerVisible(user.fullName)).toBe(true);

    await workspace.releaseOperateAs();
    expect(await workspace.isOwnerVisible(user.fullName)).toBe(false);
  });

  test("Releasing Operate As invalidates stale UI tokens in every tab", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eOperateAsTabs");
    const { users, workspace } = flowSysadminGroupAdmin;
    const page1 = workspace.browserPage;

    await users.open();
    const dialog = await users.clickOperateAs();
    await dialog.setUser(user.username);
    await dialog.submit(SYSADMIN.password);
    await workspace.waitUntilLoaded();
    await expect(workspace.operateAsBanner).toContainText(user.username);

    await workspace.toolbar.toggleFilter("templates");
    const page2 = await page1.context().newPage();
    try {
      await page2.goto(page1.url());
      await page2.getByRole("button", { name: "Templates", exact: true }).waitFor();
      const staleToken = await page2.evaluate(() => sessionStorage.getItem("id_token"));
      expect(staleToken).toBeTruthy();

      await workspace.releaseOperateAs();

      const staleResponse = await page2.evaluate(async (token) => {
        const response = await fetch("/api/v1/userDetails/whoami", {
          headers: { Authorization: `Bearer ${token}` },
        });
        return response.status;
      }, staleToken);
      expect(staleResponse).toBe(401);

      await page2.reload();
      await page2.getByRole("button", { name: "Templates", exact: true }).waitFor();
      await expect(page2.locator("span.header-info--right")).toBeHidden();
      const page2WhoAmI = await page2.evaluate(async () => {
        const tokenResponse = await fetch("/userform/ajax/inventoryOauthToken");
        const { data: token } = await tokenResponse.json();
        sessionStorage.setItem("id_token", token);
        const response = await fetch("/api/v1/userDetails/whoami", {
          headers: { Authorization: `Bearer ${token}` },
        });
        return response.json();
      });
      expect(page2WhoAmI.username).toBe(SYSADMIN.username);

      await page1.reload();
      await page1.goto("/system");
      await expect(page1.getByRole("grid", { name: "users" })).toBeVisible();

      const page3 = await page1.context().newPage();
      try {
        await page3.goto("/workspace");
        await page3.getByRole("button", { name: "Templates", exact: true }).waitFor();
        const page3WhoAmI = await page3.evaluate(async () => {
          const tokenResponse = await fetch("/userform/ajax/inventoryOauthToken");
          const { data: token } = await tokenResponse.json();
          const response = await fetch("/api/v1/userDetails/whoami", {
            headers: { Authorization: `Bearer ${token}` },
          });
          return response.json();
        });
        expect(page3WhoAmI.username).toBe(SYSADMIN.username);
      } finally {
        await page3.close();
      }
    } finally {
      await page2.close();
    }
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
    expect(await workspace.isOwnerVisible(user.fullName)).toBe(true);

    await profile.open();
    await profile.waitUntilLoaded();
    const changePassword = await profile.openChangePassword();
    await changePassword.save(SYSADMIN.password, newPassword);
    await expect(changePassword.successMessage("Password changed successfully")).toBeVisible();
    await workspace.releaseOperateAs();

    const newSession = await flowUserSession(user.username, newPassword);
    expect(await newSession.workspace.isLoaded()).toBe(true);
  });
});
