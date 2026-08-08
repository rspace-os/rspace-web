import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_FILE_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`ownCloud integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode not yet built for ownCloud");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("owncloud.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("ownCloud", { successName: "OwnCloud" });
  });

  test("As a user, I can insert a file from ownCloud into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openOwnCloudDialog();

    await dialog.selectFile(MOCK_FILE_NAME);
    await dialog.clickChoose();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_FILE_NAME);
  });
});
