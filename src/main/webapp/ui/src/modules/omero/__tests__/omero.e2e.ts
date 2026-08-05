import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const EXPECTED_MOCK_PROJECT_NAME = "Mock Omero Project";

const INTEGRATION_MODE = env.integrationMode;
const OMERO_USERNAME = INTEGRATION_MODE === "real" ? env.omeroUsername : "mock-omero-username";
const OMERO_PASSWORD = INTEGRATION_MODE === "real" ? env.omeroPassword : "mock-omero-password";

test.describe(`OMERO integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeAll(async () => {
    if (INTEGRATION_MODE === "real" && !(OMERO_USERNAME && OMERO_PASSWORD)) {
      throw new Error("OMERO_USERNAME and OMERO_PASSWORD must be set in .env / CI secrets for real mode");
    }
  });

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("omero.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledForOmero("OMERO", { username: OMERO_USERNAME, password: OMERO_PASSWORD });
  });

  test("As a user, I can insert an OMERO item into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openOmeroDialog();

    await dialog.selectFirstItem();
    await dialog.clickInsert();

    const field = await docEditor.getField("New List of Materials");
    if (INTEGRATION_MODE === "real") {
      await expect.poll(() => field.getText()).toMatch(/\/(project|screen)\//);
    } else {
      await expect.poll(() => field.getText()).toContain(EXPECTED_MOCK_PROJECT_NAME);
    }
  });
});
