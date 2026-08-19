import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { MOCK_SLACK_CHANNEL, MOCK_SLACK_WORKSPACE, type SlackWebhookMessage } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Slack integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode is out of scope for now");

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

  test("As a user, I can send a document to a connected Slack channel", async ({
    componentSlackDialog,
    componentSlackShare,
    clientDocuments,
    pageDocument,
    page,
  }) => {
    await test.step("Given I have connected a Slack channel", async () => {
      await componentSlackDialog.open();
      await componentSlackDialog.connectAndSaveChannel();
      await componentSlackDialog.close();
    });

    const docName = await test.step("And I have a document to share", async () => {
      const doc = await clientDocuments.create({ name: uniqueName("Slack share") });
      await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
      await pageDocument.isLoaded();
      return doc.name;
    });

    const message = `Please review ${docName}`;

    await test.step("When I send it to the connected Slack channel", async () => {
      await componentSlackShare.open();
      await componentSlackShare.send(MOCK_SLACK_CHANNEL, message);
    });

    const payload = await test.step("Then the Slack webhook receives the message and document attachment", async () => {
      const res = await page.request.get(`${env.mockBaseUrl}/services/mock-webhook/_lastPayload`);
      expect(res.ok()).toBe(true);
      return (await res.json()) as SlackWebhookMessage;
    });

    expect(payload.text).toContain(message);
    expect(payload.attachments).toHaveLength(1);
    expect(payload.attachments[0].title).toBe(docName);
  });
});
