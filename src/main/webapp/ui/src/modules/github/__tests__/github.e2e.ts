import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_ADVERSARIAL_FILE_PATH, MOCK_FILE_PATH, MOCK_REPO_FULL_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

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

  test("As a user, I can insert a GitHub file into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();

    const dialog = await docEditor.openGitHubDialog();
    await dialog.selectRepository(MOCK_REPO_FULL_NAME);
    await dialog.selectPath(MOCK_FILE_PATH);
    await dialog.clickInsert();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_FILE_PATH);
  });

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
