import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Login", () => {
  test("As a registered user, I can log in and land on the Workspace", async ({
    page,
    pageLogin,
    pageWorkspace,
    appUser,
  }) => {
    await pageLogin.open();

    await pageLogin.login(appUser.username, appUser.password);

    await expect(page).toHaveURL((url) => url.pathname === "/workspace");
    await expect(pageWorkspace.toolbar.createMenu.createButton).toBeVisible();
  });

  test("As a user, I can see a clear error when my credentials are invalid", async ({ page, pageLogin }) => {
    await pageLogin.open();

    await pageLogin.login("does-not-exist-e2e-check", "wrong-password");

    await expect(pageLogin.invalidCredentialsError).toBeVisible();
    await expect(page).toHaveURL((url) => url.pathname === "/login");
  });
});
