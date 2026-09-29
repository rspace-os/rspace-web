import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows/sessions/cloudSessions";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Community signup", () => {
  test("As a new community user, I must confirm my email before I can log in, the link works only once, and my affiliation is saved", async ({
    flowCloudVisitor,
    clientMailpit,
  }) => {
    const visitor = await flowCloudVisitor();
    const username = alphaNumericUnique("e2eCloudSignup");
    const email = `${username}@example.com`;
    const affiliation = uniqueName("E2E Signup Org");

    await test.step("Given I sign up", async () => {
      await visitor.signup.open();
      await visitor.signup.signUp({
        username,
        password: DYNAMIC_USER_PASSWORD,
        firstName: "E2E",
        lastName: "CloudSignup",
        email,
        affiliation,
        acceptTerms: true,
      });
      await expect(visitor.verification.heading).toHaveText("Sign Up Requested");
    });

    await test.step("Then I cannot log in until I confirm my email", async () => {
      await visitor.login.open();
      await visitor.login.login(username, DYNAMIC_USER_PASSWORD);
      await expect(visitor.page).toHaveURL(
        (url) => url.pathname === "/cloud/resendConfirmationEmail/awaitingEmailConfirmation",
      );
    });

    const activationLink = await clientMailpit.waitForLink(email, "Welcome to RSpace", "/cloud/verifysignup?token=");

    await test.step("When I activate my account from the welcome email", async () => {
      await visitor.verification.activateAccount(activationLink);
      await expect(visitor.verification.heading).toHaveText("Completed!");
    });

    await test.step("Then I can log in to the Workspace", async () => {
      await visitor.login.open();
      await visitor.login.login(username, DYNAMIC_USER_PASSWORD);
      await expect(visitor.page).toHaveURL((url) => url.pathname === "/workspace");
    });

    await test.step("And the activation link cannot be reused", async () => {
      await visitor.page.goto(activationLink);
      await expect(visitor.page).toHaveTitle(/Account Activation Failed/);
    });

    await test.step("And the directory lists me with the affiliation I signed up with", async () => {
      await visitor.directory.open();
      await visitor.directory.search(username);
      await expect(visitor.directory.userRow(username)).toContainText(affiliation);
    });
  });
});
