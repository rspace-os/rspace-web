import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.use({ storageState: { cookies: [], origins: [] } });

const REMINDER_EMAIL_SUBJECT = "RSpace username reminder";

test.describe("Username reminder", () => {
  test("As a user, I receive an email reminding me of my username", async ({
    page,
    pageLogin,
    clientSysadmin,
    clientMailpit,
  }) => {
    const username = alphaNumericUnique("e2eUserReminder");
    const email = `${username}@example.com`;
    await clientMailpit.deleteAllMessages();

    await clientSysadmin.createUser({
      username,
      password: "Passw0rd!23",
      email,
      firstName: "E2E",
      lastName: "UsernameReminder",
      role: "ROLE_USER",
    });

    await test.step("request a username reminder", async () => {
      await pageLogin.open();
      const pageRequestReminder = await pageLogin.clickForgotUsername();
      await pageRequestReminder.requestReminder(email);
      await expect(page).toHaveURL((url) => url.pathname === "/signup/usernameReminderRequest");
    });

    await test.step("read the username from the email", async () => {
      await expect
        .poll(
          async () => {
            const messages = await clientMailpit.listMessages(`to:${email}`);
            return messages.some((m) => m.Subject === REMINDER_EMAIL_SUBJECT);
          },
          { timeout: 15_000 },
        )
        .toBe(true);
      const messages = await clientMailpit.listMessages(`to:${email}`);
      const summary = messages.find((m) => m.Subject === REMINDER_EMAIL_SUBJECT);
      if (!summary) {
        throw new Error(`no "${REMINDER_EMAIL_SUBJECT}" email found for ${email}`);
      }
      const message = await clientMailpit.getMessage(summary.ID);
      expect(message.HTML).toContain(username);
    });
  });
});
