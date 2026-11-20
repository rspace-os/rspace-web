import { expect, type Page } from "@playwright/test";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { type AppUser, USERS } from "@/__tests__/e2e/users";

async function logout(page: Page) {
  await page.getByRole("button", { name: "Account Menu" }).click();
  await page.getByRole("menuitem", { name: "Log Out" }).click();
  await expect(page).toHaveURL((url) => url.pathname === "/login");
}

async function signIn(page: Page, user: AppUser) {
  await new LoginPage(page).login(user.username, user.password);
  await expect(page).toHaveURL((url) => url.pathname === "/workspace");
}

test("Logging out signs out Workspace and Gallery tabs in the same browser session", async ({
  browser,
  browserContextOptions,
  context,
  page,
  pageWorkspace,
}) => {
  const isolatedContext = await browser.newContext({
    ...browserContextOptions,
    storageState: storageStatePath(USERS.user6f.username),
  });
  try {
    const gallery = await context.newPage();
    const logoutTab = await context.newPage();
    const isolatedTab = await isolatedContext.newPage();
    await pageWorkspace.open();
    await gallery.goto("/gallery");
    await logoutTab.addInitScript(() => {
      const setItem = Storage.prototype.setItem;
      Storage.prototype.setItem = function (this: Storage, key: string, value: string) {
        if (this === localStorage && key === "rspace.logout") {
          sessionStorage.setItem("logoutSenderGuard", String(window.rspaceCrossTabLogoutStarted === true));
        }
        setItem.call(this, key, value);
      };
    });
    await logoutTab.goto("/workspace");
    await isolatedTab.goto("/workspace");
    await expect(gallery.getByRole("button", { name: "Account Menu" })).toBeVisible();
    await expect(isolatedTab.getByRole("button", { name: "Account Menu" })).toBeVisible();

    await logout(logoutTab);
    expect(await logoutTab.evaluate(() => sessionStorage.getItem("logoutSenderGuard"))).toBe("true");

    for (const tab of [page, gallery]) {
      await expect(tab).toHaveURL((url) => url.pathname === "/login");
      await expect(tab.getByRole("button", { name: "Log in" })).toBeVisible();
    }
    await isolatedTab.reload();
    await expect(isolatedTab).toHaveURL((url) => url.pathname === "/workspace");
    await expect(isolatedTab.getByRole("button", { name: "Account Menu" })).toBeVisible();
  } finally {
    await isolatedContext.close();
  }
});

test("The initiating SSO logout tab reaches the configured destination", async ({ page }) => {
  test.skip(!env.ssoLogoutUrl, "Set E2E_SSO_LOGOUT_URL to the deployment.sso.logout.url value.");
  const destination = new URL(env.ssoLogoutUrl, env.baseURL);

  await page.goto("/adminLogin/logoutFromSso", { waitUntil: "commit" });

  await expect(page).toHaveURL(destination.href);
});

for (const first of ["autosave response", "logout notification"]) {
  test(`A draft survives when the ${first} arrives first`, async ({ appUser, context, page, pageWorkspace }) => {
    const logoutTab = await context.newPage();
    await logoutTab.goto("/workspace");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const draft = "Keep these unsaved document edits after signing out.";
    const prompt = page.getByRole("alertdialog", { name: "Your session has expired" });

    const releaseAutosave = Promise.withResolvers<void>();
    await page.route(
      "**/ajax/autosaveField",
      async (route) => {
        await releaseAutosave.promise;
        await route.fulfill({ status: 403, headers: { "X-RSpace-Session": "ended" } });
      },
      { times: 1 },
    );
    await page.evaluate(() => {
      window.addEventListener("rspace:session-ended", () => {
        document.body.dataset.sessionEnded = "true";
      });
    });
    await field.body.pressSequentially(draft);

    try {
      const autosaveStarted = page.waitForRequest((request) => request.url().endsWith("/ajax/autosaveField"));
      await editor.editToolbar.saveMenuButton.click();
      await page.getByRole("menuitem", { name: "Save", exact: true }).click();
      await autosaveStarted;
      if (first === "autosave response") {
        releaseAutosave.resolve();
        await expect(prompt).toBeVisible();
      }
      await logout(logoutTab);
      await expect(page.locator("body")).toHaveAttribute("data-session-ended", "true");
      await expect(prompt.getByRole("button", { name: "Sign in", exact: true })).toBeFocused();
    } finally {
      releaseAutosave.resolve();
    }

    await page.waitForFunction(() => !Reflect.get(window, "isAutosaveInProgress")());
    await expect(prompt).toHaveCount(1);
    await expect(field.body).toHaveText(draft);
    await prompt.getByRole("button", { name: "Cancel", exact: true }).click();

    const otherUser = appUser.username === USERS.user2b.username ? USERS.user1a : USERS.user2b;
    await signIn(logoutTab, otherUser);
    const rejectedLock = page.waitForResponse(
      (response) => response.url().endsWith("/ajax/requestEdit") && response.status() === 403,
    );
    await editor.editToolbar.saveMenuButton.click();
    await page.getByRole("menuitem", { name: "Save", exact: true }).click();
    await rejectedLock;
    await expect(prompt.getByRole("button", { name: "Sign in", exact: true })).toBeFocused();
    await expect(field.body).toHaveText(draft);

    await logout(logoutTab);
    await expect(prompt).toHaveCount(1);
    await prompt.getByRole("button", { name: "Cancel", exact: true }).click();
    await signIn(logoutTab, appUser);
    await editor.saveAndView();

    const reopened = await pageWorkspace.openDocument(editor.getId());
    await expect(await reopened.getFieldViewContent("", 0)).toHaveText(draft);
  });
}
