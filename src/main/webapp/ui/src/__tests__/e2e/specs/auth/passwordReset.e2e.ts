import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.use({ storageState: { cookies: [], origins: [] } });

const NEW_PASSWORD = "NewPassw0rd!456";

test.describe("Password reset", () => {
  test("As a user, I receive a reset email and can set a new password", async ({
    page,
    pageLogin,
    pageResetPassword,
    clientSysadmin,
    clientMailpit,
  }) => {
    const username = alphaNumericUnique("e2ePwReset");
    const email = `${username}@example.com`;
    await clientMailpit.deleteAllMessages();

    const RESET_EMAIL_SUBJECT = "RSpace password change request";
    await clientSysadmin.createUser({
      username,
      password: "Passw0rd!23",
      email,
      firstName: "E2E",
      lastName: "PasswordReset",
      role: "ROLE_USER",
    });

    await test.step("request a password reset", async () => {
      await pageLogin.open();
      const pageRequestReset = await pageLogin.clickForgotPassword();
      await pageRequestReset.requestReset(email);
    });

    const resetLink = await test.step("read the reset link from the email", async () => {
      const link = await clientMailpit.waitForLink(email, RESET_EMAIL_SUBJECT, "/signup/passwordResetReply?token=");
      const token = new URL(link).searchParams.get("token");
      expect(token).toBeTruthy();
      return link;
    });

    await test.step("set a new password from the emailed link", async () => {
      await page.goto(resetLink);
      await pageResetPassword.submitNewPassword(NEW_PASSWORD);
      await expect(page).toHaveURL((url) => url.pathname === "/signup/passwordResetReply");
    });

    await test.step("log in with the new password", async () => {
      await pageLogin.open();
      await pageLogin.login(username, NEW_PASSWORD);
      await expect(page).toHaveURL((url) => url.pathname === "/workspace");
    });
  });
});
