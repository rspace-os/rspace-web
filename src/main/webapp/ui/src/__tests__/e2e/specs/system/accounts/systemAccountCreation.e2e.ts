import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

test.describe("System account creation", { tag: tags.SYSTEM }, () => {
  test("A sysadmin can create a new User account from the System page", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const username = alphaNumericUnique("e2eCreateUser");

    await createAccount.open();
    await createAccount.selectTab("User");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "CreateUser",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.noneCommunityRadio.check();
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("User");
  });

  test("Account creation requires all fields before it can be submitted", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount } = flowSysadminGroupAdmin;

    await createAccount.open();
    await createAccount.selectTab("User");
    await createAccount.createButton.click();

    expect(await createAccount.hasBlockedInvalidSubmit()).toBe(true);
  });

  test("Account creation rejects a username or email that's already taken", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount } = flowSysadminGroupAdmin;
    const email = `${alphaNumericUnique("e2eDupe")}@example.com`;

    await createAccount.open();
    await createAccount.selectTab("User");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "DupeUser",
      username: SYSADMIN.username,
      email,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.noneCommunityRadio.check();
    await createAccount.createButton.click();

    await expect(createAccount.duplicateAccountError()).toBeVisible();
  });

  test("A sysadmin can place a new User account into an existing LabGroup", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;

    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePlacePi");
    const groupName = uniqueName("e2ePlaceGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [{ username: pi.username, roleInGroup: "PI" }],
    });

    const username = alphaNumericUnique("e2ePlaceUser");
    await createAccount.open();
    await createAccount.selectTab("User");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "PlaceUser",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.allGroupsCommunityRadio.check();
    await createAccount.selectLabGroup(groupName);
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("1 group");
  });

  test("Deleting a newly created user returns available seats and total users to their original counts", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { users } = flowSysadminGroupAdmin;

    await users.open();
    const availableBefore = await users.availableSeats();
    const totalBefore = await users.totalUsers();

    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eCreateDeleteUser");

    await users.open();
    await users.deleteUser(user.username);

    await users.open();
    expect(await users.availableSeats()).toBe(availableBefore);
    expect(await users.totalUsers()).toBe(totalBefore);
  });

  test("A sysadmin can create a new PI account with a new LabGroup", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const username = alphaNumericUnique("e2eCreatePi");

    await createAccount.open();
    await createAccount.selectTab("PI");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "CreatePi",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.newLabGroupNameField.fill(uniqueName("e2eCreatePiLab"));
    await createAccount.piCommunityRadio.check();
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("PI");
  });

  test("A sysadmin can create a new Community Admin account", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const username = alphaNumericUnique("e2eCreateCommAdmin");

    await createAccount.open();
    await createAccount.selectTab("Community Admin");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "CreateCommAdmin",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.noneCommunityRadio.check();
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("Admin");
  });

  test("A sysadmin can create a new System Admin account", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const username = alphaNumericUnique("e2eCreateSysadmin");

    await createAccount.open();
    await createAccount.selectTab("System Admin");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "CreateSysadmin",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("Sysadmin");
  });

  test("A Community Admin sees a restricted Create Account form (no System Admin tab, own community only)", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
    flowUserSession,
  }) => {
    const admin = await createDynamicUser(clientSysadmin, "ROLE_ADMIN", "e2eCommRestrictedAdmin");
    const communityName = uniqueName("e2eCommRestricted");

    const { communities } = flowSysadminGroupAdmin;
    await communities.open();
    const creation = await communities.newCommunity();
    await creation.nameField.fill(communityName);
    await creation.adminCheckbox(admin.username).check();
    await creation.submitExpectingSuccess();

    const adminSession = await flowUserSession(admin.username, DYNAMIC_USER_PASSWORD);
    const { createAccount } = adminSession;
    await createAccount.open();

    await expect(createAccount.systemAdminTabLink).toHaveCount(0);
    await expect(createAccount.batchRegistrationTabLink).toHaveCount(0);

    await createAccount.selectTab("User");
    await expect(createAccount.communitySelectionCell).toContainText(communityName);
    await expect(createAccount.communitySelectionCell).not.toContainText("All Groups");

    await communities.deleteCommunity(communityName);
  });
});
