import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows/sessions/cloudSessions";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Community email change", () => {
  test("As a community user, my new email only takes effect once I confirm it from that inbox", async ({
    flowCloudUser,
    clientMailpit,
  }) => {
    const user = await flowCloudUser("e2eCloudEmail");
    const newEmail = `${uniqueName("e2eCloudNewEmail")}@example.com`;

    await test.step("Given I request an email change", async () => {
      await user.profile.open();
      const changeEmail = await user.profile.openChangeEmail();
      await changeEmail.submit(newEmail, DYNAMIC_USER_PASSWORD);
      await expect(user.profile.emailVerificationSentAlert).toBeVisible();
    });

    await test.step("Then my current email is unchanged", async () => {
      await user.profile.open();
      await expect(user.profile.email(user.email)).toBeVisible();
    });

    await test.step("When I confirm from the link sent to the new address", async () => {
      const link = await clientMailpit.waitForLink(newEmail, "Email address update", "/cloud/verifyEmailChange?token=");
      await user.verification.confirmEmailChange(link);
    });

    await test.step("Then my profile shows the new email", async () => {
      await user.profile.open();
      await expect(user.profile.email(newEmail)).toBeVisible();
    });
  });
});
