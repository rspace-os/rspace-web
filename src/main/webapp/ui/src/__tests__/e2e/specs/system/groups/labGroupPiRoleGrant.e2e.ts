import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

test.describe("PI role grant/revoke", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can grant and revoke a user's PI role", async ({ flowSysadminGroupAdmin, clientSysadmin }) => {
    const { users } = flowSysadminGroupAdmin;
    const { username } = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2ePiRoleGrant");

    await users.open();
    await users.grantPiRole(username, SYSADMIN.password);
    await expect(users.userRow(username)).toContainText("PI");

    await users.revokePiRole(username, SYSADMIN.password);
    await expect(users.userRow(username)).not.toContainText("PI");
    await expect(users.userRow(username)).toContainText("User");
  });

  test("As a sysadmin, revoking a PI role is blocked while the user is still a group's sole PI", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { users, toasts } = flowSysadminGroupAdmin;
    const { username } = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2ePiRoleBlocked");

    await users.open();
    await users.grantPiRole(username, SYSADMIN.password);
    await clientSysadmin.createGroup({
      displayName: uniqueName("e2ePiRoleBlockedGroup"),
      type: "LAB_GROUP",
      users: [{ username, roleInGroup: "PI" }],
    });

    const dialog = await users.attemptRevokePiRole(username, SYSADMIN.password);

    await expect(toasts.byText("cannot yet have PI role revoked")).toBeVisible();
    await dialog.cancel();
    await expect(users.userRow(username)).toContainText("PI");
  });
});
