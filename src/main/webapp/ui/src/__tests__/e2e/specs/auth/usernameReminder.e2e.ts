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
      const message = await clientMailpit.waitForMessage(email, REMINDER_EMAIL_SUBJECT);
      const bodyText = clientMailpit.extractText(message.HTML);
      const [, afterMarker] = bodyText.split("Your username is ");
      if (afterMarker === undefined) {
        throw new Error(`"Your username is " not found in email body: ${message.HTML}`);
      }
      expect(afterMarker.startsWith(username)).toBe(true);

      const links = clientMailpit.extractLinks(message.HTML);
      const loginLink = links.find((href) => href.endsWith("/login"));
      if (!loginLink) {
        throw new Error(`no login link found in email body: ${message.HTML}`);
      }
    });
  });
});
