import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_EQUIPMENT_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Calira integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode needs a host whose redirect URI is registered with Calira's real OAuth client — none of this project's environments are registered",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("clustermarket.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("Calira");
  });

  test("As a user, I can insert a Calira booking into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openCaliraDialog();

    await dialog.selectFirstBooking();
    await dialog.clickInsert();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_EQUIPMENT_NAME);
  });
});
