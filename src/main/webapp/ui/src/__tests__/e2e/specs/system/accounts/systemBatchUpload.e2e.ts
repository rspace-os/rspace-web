import { rmSync } from "node:fs";
import { dirname } from "node:path";
import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";
import { writeBatchCsvFixture } from "./fixtures/batchCsvFixture";

test.describe("System batch user registration", { tag: tags.SYSTEM }, () => {
  test("A sysadmin can batch-register a PI, two users, and a Lab Group via CSV", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users, directory, groupDetails } = flowSysadminGroupAdmin;

    const piUsername = alphaNumericUnique("e2eBatchPi");
    const user1Username = alphaNumericUnique("e2eBatchUser");
    const user2Username = alphaNumericUnique("e2eBatchUser");
    const groupName = uniqueName("e2eBatchGroup");

    const { batchRegistration } = createAccount;

    await createAccount.open();
    await createAccount.selectBatchRegistrationTab();
    await batchRegistration.selectCsvInputMode();
    await batchRegistration.loadCsv(
      [
        "#Users",
        `E2E,BatchPi,${piUsername}@example.com,ROLE_PI,${piUsername},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchUser,${user1Username}@example.com,ROLE_USER,${user1Username},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchUser,${user2Username}@example.com,ROLE_USER,${user2Username},${DYNAMIC_USER_PASSWORD}`,
        "",
        "#Groups",
        `${groupName},${piUsername},${user1Username},${user2Username}`,
      ].join("\n"),
    );

    await expect(batchRegistration.userRow(piUsername)).toBeVisible();
    await expect(batchRegistration.userRow(user1Username)).toBeVisible();
    await expect(batchRegistration.userRow(user2Username)).toBeVisible();
    await expect(batchRegistration.groupRow(groupName)).toBeVisible();

    await batchRegistration.createAllButton.click();
    await expect(batchRegistration.userRow(piUsername)).toContainText("User created");
    await expect(batchRegistration.userRow(user1Username)).toContainText("User created");
    await expect(batchRegistration.userRow(user2Username)).toContainText("User created");
    await expect(batchRegistration.groupRow(groupName)).toContainText("Group created");

    await users.open();
    await users.search(piUsername);
    await expect(users.userRow(piUsername)).toContainText("PI");
    await users.search(user1Username);
    await expect(users.userRow(user1Username)).toContainText("User");

    const groupId = await directory.findGroupIdForUser(piUsername, groupName);
    await groupDetails.openGroup(groupId);
    await expect(groupDetails.memberRow(piUsername)).toBeVisible();
    await expect(groupDetails.memberRow(user1Username)).toBeVisible();
    await expect(groupDetails.memberRow(user2Username)).toBeVisible();
  });

  test("Batch upload validates required fields, min/max lengths, and uniqueness before creating", async ({
    flowSysadminGroupAdmin,
  }) => {
    const { createAccount } = flowSysadminGroupAdmin;
    const { batchRegistration } = createAccount;

    const piUsername = alphaNumericUnique("e2eBatchValPi");
    const user1Username = alphaNumericUnique("e2eBatchValU1");
    const user2Username = alphaNumericUnique("e2eBatchValU2");

    await createAccount.open();
    await createAccount.selectBatchRegistrationTab();
    await batchRegistration.selectCsvInputMode();
    await batchRegistration.loadCsv(
      [
        "#Users",
        `E2E,BatchValPi,${piUsername}@example.com,ROLE_PI,${piUsername},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchValU1,${user1Username}@example.com,ROLE_USER,${user1Username},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchValU2,${user2Username}@example.com,ROLE_USER,${user2Username},${DYNAMIC_USER_PASSWORD}`,
      ].join("\n"),
    );

    const row1 = batchRegistration.userRowAt(1);
    const row2 = batchRegistration.userRowAt(2);

    for (const [field, restoreValue] of [
      [row1.firstName, "E2E"],
      [row1.lastName, "BatchValU1"],
      [row1.email, `${user1Username}@example.com`],
      [row1.username, user1Username],
      [row1.password, DYNAMIC_USER_PASSWORD],
    ] as const) {
      await field.fill("");
      await batchRegistration.clickCreateAll();
      expect(await createAccount.hasBlockedInvalidSubmit()).toBe(true);
      await field.fill(restoreValue);
    }

    await row1.password.fill("x");
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(1);
    await row2.password.fill("y");
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(2);
    await row1.password.fill(DYNAMIC_USER_PASSWORD);
    await row2.password.fill(DYNAMIC_USER_PASSWORD);

    await row1.username.fill("xx");
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(1);
    await row2.username.fill("x".repeat(51));
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(2);
    await row1.username.fill(user1Username);
    await row2.username.fill(user2Username);

    await row1.username.fill("e2eBatchValDupe");
    await row2.username.fill("e2eBatchValDupe");
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(2);
    await row1.username.fill(user1Username);
    await row2.username.fill(user2Username);

    await row1.email.fill("dupe@example.com");
    await row2.email.fill("dupe@example.com");
    await batchRegistration.clickCreateAll();
    expect(await batchRegistration.validationErrorCount()).toBe(2);
    await row1.email.fill(`${user1Username}@example.com`);
    await row2.email.fill(`${user2Username}@example.com`);

    expect(await batchRegistration.userRowCount()).toBe(3);
    await batchRegistration.removeUserRowAt(1);
    expect(await batchRegistration.userRowCount()).toBe(2);
  });

  test("A sysadmin can manually add and register a User via Batch User Registration", async ({
    flowSysadminGroupAdmin,
  }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const { batchRegistration } = createAccount;
    const username = alphaNumericUnique("e2eBatchManualUser");

    await createAccount.open();
    await createAccount.selectBatchRegistrationTab();
    await batchRegistration.selectManualCreationMode();
    await batchRegistration.addUserRow();

    const row = batchRegistration.userRowAt(0);
    await row.firstName.fill("E2E");
    await row.lastName.fill("BatchManualUser");
    await row.email.fill(`${username}@example.com`);
    await row.username.fill(username);
    await row.password.fill(DYNAMIC_USER_PASSWORD);

    await batchRegistration.clickCreateAll();
    await expect(row.status).toContainText("User created");

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("User");
  });

  test("A sysadmin can batch-register users by uploading a real CSV file", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const { batchRegistration } = createAccount;
    const piUsername = alphaNumericUnique("e2eBatchUploadPi");
    const userUsername = alphaNumericUnique("e2eBatchUploadUser");

    await createAccount.open();
    await createAccount.selectBatchRegistrationTab();
    await batchRegistration.selectCsvInputMode();
    await batchRegistration.uploadCsvFile(
      [
        "#Users",
        `E2E,BatchUploadPi,${piUsername}@example.com,ROLE_PI,${piUsername},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchUploadUser,${userUsername}@example.com,ROLE_USER,${userUsername},${DYNAMIC_USER_PASSWORD}`,
      ].join("\n"),
    );

    await expect(batchRegistration.userRow(piUsername)).toBeVisible();
    await expect(batchRegistration.userRow(userUsername)).toBeVisible();

    await batchRegistration.createAllButton.click();
    await expect(batchRegistration.userRow(piUsername)).toContainText("User created");
    await expect(batchRegistration.userRow(userUsername)).toContainText("User created");

    await users.open();
    await users.search(piUsername);
    await expect(users.userRow(piUsername)).toContainText("PI");
  });

  test("A sysadmin can batch-register users by uploading a CSV file from a real path on disk", async ({
    flowSysadminGroupAdmin,
  }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;
    const { batchRegistration } = createAccount;
    const piUsername = alphaNumericUnique("e2eBatchUploadPathPi");
    const userUsername = alphaNumericUnique("e2eBatchUploadPathUser");

    const csvPath = writeBatchCsvFixture(
      [
        "#Users",
        `E2E,BatchUploadPathPi,${piUsername}@example.com,ROLE_PI,${piUsername},${DYNAMIC_USER_PASSWORD}`,
        `E2E,BatchUploadPathUser,${userUsername}@example.com,ROLE_USER,${userUsername},${DYNAMIC_USER_PASSWORD}`,
      ].join("\n"),
    );

    try {
      await createAccount.open();
      await createAccount.selectBatchRegistrationTab();
      await batchRegistration.selectCsvInputMode();
      await batchRegistration.uploadCsvFileFromPath(csvPath);

      await expect(batchRegistration.userRow(piUsername)).toBeVisible();
      await expect(batchRegistration.userRow(userUsername)).toBeVisible();

      await batchRegistration.createAllButton.click();
      await expect(batchRegistration.userRow(piUsername)).toContainText("User created");
      await expect(batchRegistration.userRow(userUsername)).toContainText("User created");

      await users.open();
      await users.search(piUsername);
      await expect(users.userRow(piUsername)).toContainText("PI");
    } finally {
      rmSync(dirname(csvPath), { recursive: true, force: true });
    }
  });
});
