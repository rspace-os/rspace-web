import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { StatusClient } from "@/__tests__/e2e/api/clients/StatusClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";
import { fixturePath, uniqueName } from "@/__tests__/e2e/testData";

const PROFILE_IMAGE = fixturePath(import.meta.url, "../inventory/fixtures/add_sample_image.png");

test.describe("My RSpace profile", () => {
  test("As a user, I can update my name and email", async ({ pageMyRSpace, appUser }) => {
    const firstName = uniqueName("First");
    const lastName = uniqueName("Last");
    const email = `${uniqueName("profile")}@example.com`;

    await pageMyRSpace.open();
    const profile = await pageMyRSpace.openProfile();
    expect(await profile.isProfileEditable()).toBe(true);

    const editProfile = await profile.openEditProfile();
    await editProfile.submit(firstName, lastName);
    await expect(profile.firstName(firstName)).toBeVisible();
    await expect(profile.lastName(lastName)).toBeVisible();

    const changeEmail = await profile.openChangeEmail();
    await changeEmail.submit(email, appUser.password);
    await expect(profile.email(email)).toBeVisible();
  });

  test("As a user, I can upload a valid profile image", async ({ pageMyRSpace }) => {
    await pageMyRSpace.open();
    const profile = await pageMyRSpace.openProfile();
    const upload = await profile.openUploadImage();
    await upload.upload(PROFILE_IMAGE);
    await profile.waitUntilLoaded();
  });

  test("As a user, uploading an invalid or too-large profile image is rejected", async ({ pageMyRSpace }) => {
    await pageMyRSpace.open();
    const profile = await pageMyRSpace.openProfile();

    let upload = await profile.openUploadImage();
    await upload.uploadExpectingValidationError({
      name: "not-an-image.mp3",
      mimeType: "audio/mpeg",
      buffer: Buffer.from("fake mp3 content"),
    });
    await expect(upload.message).toContainText("The file type is not an image.");
    await upload.cancel();

    upload = await profile.openUploadImage();
    await upload.uploadExpectingValidationError({
      name: "large.png",
      mimeType: "image/png",
      buffer: Buffer.alloc(1_100_000),
    });
    await expect(upload.message).toContainText("less than 1000kB");
    await upload.cancel();
  });

  test("As a user, my revoked API key can be regenerated", async ({
    pageMyRSpace,
    appUser,
    apiContext,
    clientStatus,
  }) => {
    await pageMyRSpace.open();
    const profile = await pageMyRSpace.openProfile();
    expect(await profile.isApiKeyManagementVisible()).toBe(true);
    await profile.revokeApiKey();

    expect(await clientStatus.isApiKeyValid()).toBe(false);

    const newKey = await profile.regenerateApiKey(appUser.password);
    expect(newKey).not.toBe(appUser.apiKey);
    expect(await new StatusClient(apiContext, newKey).isApiKeyValid()).toBe(true);
  });

  test("As a user, I can change my password", async ({ page, pageMyRSpace, pageLogin, appUser }) => {
    const newPassword = "NewPassword123";
    await pageMyRSpace.open();
    const profile = await pageMyRSpace.openProfile();

    let changePassword = await profile.openChangePassword();
    await changePassword.submit("WrongPassword", newPassword, newPassword);
    await expect(changePassword.message).toContainText("The current password is incorrect");
    await changePassword.cancel();

    changePassword = await profile.openChangePassword();
    await changePassword.submit(appUser.password, newPassword, "MismatchedPassword456");
    await expect(changePassword.message).toContainText("Password and Confirm Password fields are not identical");
    await changePassword.cancel();

    changePassword = await profile.openChangePassword();
    await changePassword.submit(appUser.password, newPassword, newPassword);
    await changePassword.root.waitFor({ state: "hidden" });

    await profile.header.logOut();
    await pageLogin.open();
    await pageLogin.login(appUser.username, newPassword);
    await page.waitForURL((url) => url.pathname === "/workspace");
  });

  test("As a user, my API key section shows a disabled message once a sysadmin denies API access", async ({
    pageMyRSpace,
    flowSysadminConfig,
  }) => {
    const original = (await flowSysadminConfig.getSetting("api.available")).trim() as SystemPropertyValue;
    try {
      await flowSysadminConfig.setSetting("api.available", "DENIED");

      await pageMyRSpace.open();
      const profile = await pageMyRSpace.openProfile();
      await expect(profile.apiDisabledMessage).toBeVisible();
    } finally {
      await flowSysadminConfig.ensureSetting("api.available", original);
    }
  });

  test("As a user, I can view a group member's mini-profile from their record and open their real profile", async ({
    apiContext,
    flowFreshPiPermissions,
    pageWorkspace,
  }) => {
    const docName = uniqueName("e2e-miniprofile-doc");
    const member = await flowFreshPiPermissions("e2eMiniProfileMember");

    await new DocumentsClient(apiContext, member.apiKey).create({ name: docName });

    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(docName);
    const popover = await pageWorkspace.table.openOwnerMiniProfile(docName);
    await expect(popover.emailLink(`${member.username}@example.com`)).toBeVisible();
    await expect(popover.accountStatus).toHaveText("Enabled");
    await expect(popover.groupLink(member.groupName)).toBeVisible();
    await expect(popover.sendMessageLink).toBeVisible();

    const memberProfile = await popover.openProfile();
    await expect(memberProfile.groupLink(member.groupName)).toBeVisible();
  });
});
