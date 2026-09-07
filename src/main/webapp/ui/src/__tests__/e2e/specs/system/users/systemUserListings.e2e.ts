import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";

function isSortedAscending(values: string[]): boolean {
  return values.every((value, i) => i === 0 || values[i - 1].localeCompare(value) <= 0);
}

function isSortedDescending(values: string[]): boolean {
  return values.every((value, i) => i === 0 || values[i - 1].localeCompare(value) >= 0);
}

// Usage values render as e.g. "0 B"/"377 kB"
function parseFileSize(value: string): number {
  const trimmed = value.trim();
  const units: Array<[string, number]> = [
    ["GB", 1024 * 1024 * 1024],
    ["MB", 1024 * 1024],
    ["kB", 1024],
    ["B", 1],
  ];
  for (const [unit, multiplier] of units) {
    if (trimmed.endsWith(unit)) {
      return (
        Number(
          trimmed
            .slice(0, trimmed.length - unit.length)
            .trim()
            .replaceAll(",", ""),
        ) * multiplier
      );
    }
  }
  return Number.NaN;
}

function isSortedAscendingNumerically(values: number[]): boolean {
  return values.every((value, i) => i === 0 || values[i - 1] <= value);
}

function isSortedDescendingNumerically(values: number[]): boolean {
  return values.every((value, i) => i === 0 || values[i - 1] >= value);
}

test.describe("System User Listings", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can sort the Users grid by username, usage, and full name", async ({
    flowSysadminGroupAdmin,
  }) => {
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    await users.sortColumnAscending("Username");
    expect(isSortedAscending(await users.columnValues("username"))).toBe(true);

    await users.sortColumnDescending("Username");
    expect(isSortedDescending(await users.columnValues("username"))).toBe(true);

    await users.sortColumnAscending("Usage");
    expect(isSortedAscendingNumerically((await users.columnValues("fileUsage")).map(parseFileSize))).toBe(true);

    await users.sortColumnDescending("Usage");
    expect(isSortedDescendingNumerically((await users.columnValues("fileUsage")).map(parseFileSize))).toBe(true);

    await users.sortColumnAscending("Full Name");
    expect(isSortedAscending(await users.columnValues("fullNameSurnameFirst"))).toBe(true);

    await users.sortColumnDescending("Full Name");
    expect(isSortedDescending(await users.columnValues("fullNameSurnameFirst"))).toBe(true);
  });

  test("As a sysadmin, I can search the Users grid by username", async ({ clientSysadmin, flowSysadminGroupAdmin }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsSearch");
    const { users } = flowSysadminGroupAdmin;

    await users.open();
    await users.search(user.username);

    await expect(users.userRow(user.username)).toBeVisible();
  });

  test("As a sysadmin, I can page through the Users grid", async ({ flowSysadminGroupAdmin }) => {
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const firstPageUsernames = await users.columnValues("username");
    const firstPageRowCount = await users.rowCount();

    await users.goToNextPage();
    const secondPageUsernames = await users.columnValues("username");
    expect(secondPageUsernames).not.toEqual(firstPageUsernames);

    await users.goToPreviousPage();
    expect(await users.columnValues("username")).toEqual(firstPageUsernames);
    expect(await users.rowCount()).toBe(firstPageRowCount);
  });

  test("As a sysadmin, I can find a user by searching for their tag", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsTagSearch");
    const tag = alphaNumericUnique("e2eTag");
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const tagDialog = await users.openTagsDialog(user.username);
    await tagDialog.addTag(tag);
    await tagDialog.save();

    await users.searchByTag(tag);
    await expect(users.userRow(user.username)).toBeVisible();
  });

  test("As a sysadmin, I can filter the Users grid by tag and see the Tags column", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsTagFilter");
    const tag = alphaNumericUnique("e2eTag");
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const tagDialog = await users.openTagsDialog(user.username);
    await tagDialog.addTag(tag);
    await tagDialog.save();

    await users.search("");
    await users.toggleColumn("Tags", true);
    await users.filterByTag(tag);

    await expect(users.userRow(user.username)).toBeVisible();
    const tagsList = await users.showTagsList(user.username);
    await expect(tagsList.getByRole("listitem")).toHaveText(tag);
    await users.closeTagsList();
  });

  test("As a sysadmin, I can remove a tag from a user", async ({ clientSysadmin, flowSysadminGroupAdmin }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsTagRemove");
    const tag = alphaNumericUnique("e2eTag");
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const addDialog = await users.openTagsDialog(user.username);
    await addDialog.addTag(tag);
    await addDialog.save();

    const removeDialog = await users.openTagsDialog(user.username);
    await removeDialog.removeTag(tag);
    await removeDialog.save();

    await users.toggleColumn("Tags", true);
    await expect(users.tagsShowListButton(user.username)).toHaveCount(0);
  });

  test("As a sysadmin, I can apply an existing tag to another user from suggestions", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const firstUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsTagSuggestA");
    const secondUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsTagSuggestB");
    const tag = alphaNumericUnique("e2eTag");
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const firstDialog = await users.openTagsDialog(firstUser.username);
    await firstDialog.addTag(tag);
    await firstDialog.save();

    const secondDialog = await users.openTagsDialog(secondUser.username);
    await secondDialog.addTag(tag, { chooseExisting: true });
    await secondDialog.save();

    await users.toggleColumn("Tags", true);
    const tagsList = await users.showTagsList(secondUser.username);
    await expect(tagsList.getByRole("listitem")).toHaveText(tag);
    await users.closeTagsList();
  });

  test("As a sysadmin, the Users grid Actions menu exposes the sensitive account-management actions", async ({
    clientSysadmin,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsSensitiveMenu");
    const { users } = flowSysadminGroupAdmin;
    await users.open();

    const menu = await users.openActionsMenu(user.username);

    await expect(menu.getByRole("menuitem", { name: "Grant PI role", exact: true })).toBeVisible();
    await expect(menu.getByRole("menuitem", { name: "Add/Remove Tags", exact: true })).toBeVisible();

    await expect(menu.getByRole("menuitem", { name: "Export Work", exact: true })).toBeVisible();
    await expect(menu.getByRole("menuitem", { name: "Disable", exact: true })).toBeVisible();
  });

  test.describe("Account lockout", () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test("As a sysadmin, I can unlock a user's account after it locks from failed logins", async ({
      clientSysadmin,
      flowSysadminGroupAdmin,
      pageLogin,
      pageWorkspace,
    }) => {
      const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eUserListingsUnlock");

      await pageLogin.open();
      for (let attempt = 0; attempt < 4; attempt++) {
        await pageLogin.login(user.username, "wrong-password");
        await expect(pageLogin.invalidCredentialsError).toBeVisible();
      }

      await pageLogin.login(user.username, DYNAMIC_USER_PASSWORD);
      await expect(pageLogin.invalidCredentialsError).toBeVisible();

      const { users } = flowSysadminGroupAdmin;
      await users.open();
      await users.unlockUser(user.username);
      await expect(flowSysadminGroupAdmin.toasts.byVariant("success", "Successfully unlocked account.")).toBeVisible();

      await pageLogin.open();
      await pageLogin.login(user.username, DYNAMIC_USER_PASSWORD);
      await expect(pageWorkspace.toolbar.createMenu.createButton).toBeVisible();
    });
  });
});
