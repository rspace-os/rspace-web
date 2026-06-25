import { expect, tags, test } from "@/__tests__/e2e/fixtures";

test.describe(`Login ${tags.SMOKE}`, () => {
  test("As a registered user, I can log in and land on the Workspace", async ({
    page,
    pageLogin,
    pageWorkspace,
    appUser,
  }) => {
    await test.step("Given I am on the login page", async () => {
      await pageLogin.open();
    });

    await test.step("When I log in with valid credentials", async () => {
      await pageLogin.login(appUser.username, appUser.password);
    });

    await test.step("Then I land on the Workspace", async () => {
      await expect(page).toHaveURL(/\/workspace/);
      await expect(page).toHaveTitle(/Workspace/);
      expect(await pageWorkspace.isLoaded()).toBe(true);
    });
  });

  test("As a user, I see a clear error when my credentials are invalid", async ({ pageLogin }) => {
    await test.step("Given I am on the login page", async () => {
      await pageLogin.open();
    });

    await test.step("When I submit a username/password that don't exist", async () => {
      await pageLogin.login("does-not-exist-e2e-check", "wrong-password");
    });

    await test.step("Then I see an invalid-credentials error and stay on the login page", async () => {
      await expect(pageLogin.invalidCredentialsError).toBeVisible();
    });
  });
});
