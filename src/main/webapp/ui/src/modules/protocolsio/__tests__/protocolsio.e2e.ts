import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_PROTOCOL_TITLE, MOCK_PROTOCOLS_IO_PROTOCOL_RESPONSE, MOCK_PROTOCOLS_IO_SEARCH_RESPONSE } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`protocols.io integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode out of scope by design: automating protocols.io's real login page is outside this suite's boundary",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("protocols_io.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("protocols.io", { successName: "Protocols IO" });
  });

  test("As a user, I can import a protocol into a document field", async ({ page, pageWorkspace }) => {
    await page.route(/^https:\/\/www\.protocols\.io\/api\/v3\/protocols\?/, (route) =>
      route.fulfill({ json: MOCK_PROTOCOLS_IO_SEARCH_RESPONSE }),
    );
    await page.route(/^https:\/\/www\.protocols\.io\/api\/v3\/protocols\/\d+$/, (route) =>
      route.fulfill({ json: MOCK_PROTOCOLS_IO_PROTOCOL_RESPONSE }),
    );

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openProtocolsIoDialog();

    await dialog.selectProtocol(MOCK_PROTOCOL_TITLE);
    await dialog.clickImport();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(MOCK_PROTOCOL_TITLE);
  });
});
