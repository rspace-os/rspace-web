import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;
const DSW_ALIAS = INTEGRATION_MODE === "real" ? "real" : "mock";
const DSW_SERVER_URL = INTEGRATION_MODE === "real" ? env.dswServerUrl : env.mockBackendBaseUrl;
const DSW_API_KEY = INTEGRATION_MODE === "real" ? env.dswApiKey : "mock-dsw-token";

test.describe(`DSW / FAIR Wizard integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(DSW_SERVER_URL && DSW_API_KEY),
    "real mode needs DSW_SERVER_URL and DSW_API_KEY in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dsw.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithMultiConnection("DSW / FAIR Wizard", {
      aliasFieldLabel: "Label",
      aliasValue: DSW_ALIAS,
      serverUrl: DSW_SERVER_URL,
      apiKey: DSW_API_KEY,
      configuredFormAriaLabelPrefix: "Configured DSW with label",
    });
  });

  test("As a user, I can import a DSW project into the Gallery as a DMP", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    const dialog = await pageGallery.openDSWImport(DSW_ALIAS);
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();

    await expect(componentToasts.byVariant("success", planName)).toBeVisible();

    await pageGallery.open();
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(planName)).toBeVisible();
  });
});
