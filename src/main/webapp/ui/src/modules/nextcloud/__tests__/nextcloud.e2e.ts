import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_FILE_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

async function connectToNextcloudViaLogin(popup: Page): Promise<void> {
  await popup.getByRole("button", { name: "Log in", exact: true }).click();
  await popup.getByRole("textbox", { name: "Account name or email" }).fill(env.nextcloudUsername);
  await popup.getByRole("textbox", { name: "Password" }).fill(env.nextcloudPassword);
  await popup.getByRole("button", { name: "Log in", exact: true }).click();
  await popup.getByRole("button", { name: "Grant access" }).click();
}

test.describe(`Nextcloud integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.nextcloudUsername && env.nextcloudPassword),
    "real mode needs NEXTCLOUD_USERNAME/NEXTCLOUD_PASS in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("nextcloud.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("Nextcloud", {
      onExternalAuth: INTEGRATION_MODE === "real" ? connectToNextcloudViaLogin : undefined,
    });
  });

  test("As a user, I can insert a file from Nextcloud into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openNextcloudDialog();

    if (INTEGRATION_MODE === "real") {
      const fileName = await dialog.selectFirstFile();
      await dialog.clickChoose();

      const field = await docEditor.getField("New List of Materials");
      await expect.poll(() => field.getText()).toContain(fileName);
      return;
    }

    await dialog.selectFile(MOCK_FILE_NAME);
    await dialog.clickChoose();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_FILE_NAME);
  });
});
