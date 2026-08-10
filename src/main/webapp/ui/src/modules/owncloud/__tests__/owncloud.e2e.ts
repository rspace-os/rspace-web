import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_FILE_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

async function connectToOwnCloudViaLogin(popup: Page): Promise<void> {
  await popup.getByRole("textbox", { name: "Username or email" }).fill(env.ownCloudUsername);
  await popup.getByRole("textbox", { name: "Password" }).fill(env.ownCloudPassword);
  await popup.getByRole("button", { name: "Submit" }).click();
  await popup.getByRole("button", { name: "Authorise" }).click();
}

test.describe(`ownCloud integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.ownCloudUsername && env.ownCloudPassword),
    "real mode needs OWNCLOUD_USERNAME/OWNCLOUD_PASS in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("owncloud.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("ownCloud", {
      successName: "OwnCloud",
      onExternalAuth: INTEGRATION_MODE === "real" ? connectToOwnCloudViaLogin : undefined,
    });
  });

  test("As a user, I can insert a file from ownCloud into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openOwnCloudDialog();

    if (INTEGRATION_MODE === "real") {
      await dialog.expandFolder("testFolder");
      const fileName = "ownCloud Manual.pdf";
      await dialog.selectFile(fileName);
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
