import { expect, type Page } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_ADVERSARIAL_FILE_PATH, MOCK_FILE_PATH, MOCK_REPO_FULL_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

async function expectPageToScroll(page: Page): Promise<void> {
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  // Wheel over the page margin, outside the editor iframe and its own scroll area.
  await page.mouse.move(5, 300);
  await page.mouse.wheel(0, 400);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
}

test.describe(`GitHub integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode out of scope by design: no GitHub OAuth app is provisioned for this suite, and automating github.com's real login/consent page is outside this suite's boundary",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("github.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageGitHubAppsCard }) => {
    await pageGitHubAppsCard.connectAndLinkRepository(MOCK_REPO_FULL_NAME);
  });

  for (const action of ["Cancel", "Insert"] as const) {
    test(`As a user, I can scroll the document after GitHub ${action}`, async ({ page, pageWorkspace }) => {
      await pageWorkspace.open();
      const docEditor = await pageWorkspace.createBasicDocument();
      const field = await docEditor.getField("New List of Materials");
      await field.fill("Document scrolling regression\n".repeat(60));
      await expectPageToScroll(page);
      const overflowBefore = await page.evaluate(() => document.body.style.overflow);

      const dialog = await docEditor.openGitHubDialog();
      if (action === "Insert") {
        await dialog.selectRepository(MOCK_REPO_FULL_NAME);
        await dialog.selectPath(MOCK_FILE_PATH);
        await dialog.clickInsert();
        await expect.poll(() => field.getText()).toContain(MOCK_FILE_PATH);
      } else {
        await dialog.close();
      }

      await expect.poll(() => page.evaluate(() => document.body.style.overflow)).toBe(overflowBefore);
      await expectPageToScroll(page);
    });
  }

  test.skip("As a user, a file name containing HTML metacharacters renders as literal text, not markup", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();

    const dialog = await docEditor.openGitHubDialog();
    await dialog.selectRepository(MOCK_REPO_FULL_NAME);

    // An exact-name match only succeeds if the tags/quotes rendered as
    // literal characters rather than being parsed as real markup.
    await expect(dialog.frame.getByRole("link", { name: MOCK_ADVERSARIAL_FILE_PATH, exact: true })).toBeVisible();
  });
});
