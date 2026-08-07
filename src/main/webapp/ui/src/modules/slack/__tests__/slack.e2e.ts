import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_SLACK_WORKSPACE } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Slack integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "out of scope");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("slack.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Slack", true);
  });

  test("As a user, I can connect a Slack channel via OAuth", async ({ componentToasts, componentSlackDialog }) => {
    const dialog = componentSlackDialog;

    await dialog.open();
    await dialog.connectAndSaveChannel();

    await expect(componentToasts.byVariant("success", "Successfully added channel.")).toBeVisible();

    await dialog.open();
    await expect(dialog.savedChannelText(MOCK_SLACK_WORKSPACE)).toBeVisible();
  });
});
