import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import type { MsTeamsAdaptiveCardMessage } from "./mock";

const INTEGRATION_MODE = env.integrationMode;
const MSTEAMS_WEBHOOK_URL =
  INTEGRATION_MODE === "real" ? env.msteamsWebhookUrl : `${env.mockBackendBaseUrl}/msteams/webhook`;
const CHANNEL_NAME = "e2e-teams-channel";

test.describe(`Microsoft Teams integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real" && !MSTEAMS_WEBHOOK_URL, "real mode needs MSTEAMS_WEBHOOK_URL");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("msteams.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithWebhook("Teams", {
      channelName: CHANNEL_NAME,
      webhookUrl: MSTEAMS_WEBHOOK_URL,
    });
  });

  test("As a user, I can share a document to a Teams channel via a webhook", async ({
    clientDocuments,
    pageDocument,
    componentMsTeamsShare,
    page,
  }) => {
    const docName = await test.step("Given I have a document to share", async () => {
      const doc = await clientDocuments.create({ name: uniqueName("Teams share") });
      await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
      await pageDocument.isLoaded();
      return doc.name;
    });

    const message = `Please review ${docName}`;

    await test.step("When I share it to the configured Teams channel", async () => {
      await componentMsTeamsShare.open();
      await componentMsTeamsShare.send(CHANNEL_NAME, message);
    });

    if (INTEGRATION_MODE === "real") {
      return;
    }

    const payload = await test.step("Then the Teams webhook receives an Adaptive Card message", async () => {
      const res = await page.request.get(`${env.mockBaseUrl}/msteams/webhook/_lastPayload`);
      expect(res.ok()).toBe(true);
      return (await res.json()) as MsTeamsAdaptiveCardMessage;
    });

    expect(payload.type).toBe("message");
    expect(payload.attachments).toHaveLength(1);
    const [attachment] = payload.attachments;
    expect(attachment.contentType).toBe("application/vnd.microsoft.card.adaptive");

    const card = attachment.content;
    expect(card.type).toBe("AdaptiveCard");
    const bodyText = JSON.stringify(card.body);
    expect(bodyText).toContain(message);
    expect(bodyText).toContain(docName);
  });
});
