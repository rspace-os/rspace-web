import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import {
  MOCK_PROTOCOL_ID,
  MOCK_PROTOCOL_TITLE,
  MOCK_PROTOCOLS_IO_PROTOCOL_RESPONSE,
  MOCK_PROTOCOLS_IO_SEARCH_RESPONSE,
} from "./mock";

const INTEGRATION_MODE = env.integrationMode;

const REAL_PROTOCOL_TITLE = "RSpace Test Protocol";

async function connectToProtocolsIoViaLogin(popup: Page): Promise<void> {
  const cookieBanner = popup.getByRole("button", { name: "Accept all cookies" });
  const cookieBannerAppeared = await cookieBanner
    .waitFor({ state: "visible", timeout: 8_000 })
    .then(() => true)
    .catch(() => false);
  if (cookieBannerAppeared) {
    await cookieBanner.click();
  }

  await popup.getByRole("textbox", { name: "Email" }).fill(env.protocolsioUsername);
  await popup.getByRole("textbox", { name: "Password" }).fill(env.protocolsioPassword);
  await popup.getByRole("button", { name: "Sign in" }).click();
}

test.describe(`protocols.io integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.protocolsioUsername && env.protocolsioPassword),
    "real mode needs PROTOCOLIO_USERNAME/PROTOCOLIO_PASSWORD in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("protocols_io.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("protocols.io", {
      successName: "Protocols IO",
      onExternalAuth: INTEGRATION_MODE === "real" ? connectToProtocolsIoViaLogin : undefined,
    });
  });

  test("As a user, I can import a protocol into a document field", async ({ page, pageWorkspace }) => {
    if (INTEGRATION_MODE !== "real") {
      await page.route("https://www.protocols.io/api/v3/protocols?**", (route) =>
        route.fulfill({ json: MOCK_PROTOCOLS_IO_SEARCH_RESPONSE }),
      );
      await page.route(`https://www.protocols.io/api/v3/protocols/${MOCK_PROTOCOL_ID}`, (route) =>
        route.fulfill({ json: MOCK_PROTOCOLS_IO_PROTOCOL_RESPONSE }),
      );
    }

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openProtocolsIoDialog();

    const title = INTEGRATION_MODE === "real" ? REAL_PROTOCOL_TITLE : MOCK_PROTOCOL_TITLE;
    await dialog.selectProtocol(title);
    await dialog.clickImport();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(title);
  });
});
