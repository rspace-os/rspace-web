import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("Signup", () => {
  test("As a new user, I can self-register and land on the Workspace", async ({ page, pageSignup, pageWorkspace }) => {
    const username = alphaNumericUnique("e2eSignup");
    const email = `${username}@example.com`;

    await pageSignup.open();
    await pageSignup.signUp({
      username,
      password: "Passw0rd!23",
      firstName: "E2E",
      lastName: "Signup",
      email,
    });

    await expect(page).toHaveURL((url) => url.pathname === "/workspace");
    await expect(pageWorkspace.toolbar.createMenu.createButton).toBeVisible();
  });
});
