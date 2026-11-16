import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";

test.use({ storageState: { cookies: [], origins: [] } });

test("login waits for consecutive failures and then success", async ({ page, pageLogin: login }) => {
  await page.context().route("**/*", async (route) => {
    const url = new URL(route.request().url());
    if (url.origin === "http://login.test" || ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)) {
      await route.continue();
    } else {
      await route.abort("blockedbyclient");
    }
  });
  let submissions = 0;
  await page.route("http://login.test/**", async (route) => {
    if (route.request().method() === "POST") submissions++;
    const body =
      submissions === 3
        ? "<h1>Workspace</h1>"
        : `<form method="post" action="/login">
            <label>User<input name="username"></label>
            <label>Password<input name="password" type="password"></label>
            <button>Log in</button>
          </form>${submissions ? "Invalid username or password, please try again." : ""}`;
    await route.fulfill({ contentType: "text/html", body });
  });
  await page.goto("http://login.test/login");
  for (let attempt = 1; attempt <= 2; attempt++) {
    await login.login("user", "wrong");
    expect(submissions).toBe(attempt);
    await expect(login.invalidCredentialsError).toBeVisible();
  }
  await login.login("user", "correct");
  expect(submissions).toBe(3);
  await expect(page.getByRole("heading", { name: "Workspace" })).toBeVisible();
});
