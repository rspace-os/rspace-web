import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_EGNYTE_FILE_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Egnyte integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode out of scope");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("egnyte.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("Egnyte", env.mockBaseUrl, {
      textboxName: "Egnyte Domain URL",
    });
  });

  test("As a user, I can insert a link to an Egnyte file into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();

    const dialog = await docEditor.openEgnyteDialog();
    await dialog.waitForPicker();
    await dialog.selectFile(MOCK_EGNYTE_FILE_NAME);
    await dialog.confirmSelection();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_EGNYTE_FILE_NAME);
  });
});
