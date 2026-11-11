import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { DSW_MOCK_PATH, DSW_V4_MOCK_PATH } from "./mock";

const INTEGRATION_MODE = env.integrationMode;
const DSW_ALIAS = INTEGRATION_MODE === "real" ? "real" : "mock";
/*
 * PRT-1135 requires at least two DMP Import items from one integration. A second DSW connection
 * provides that state. Distinct aliases keep the menu-item locators unambiguous.
 */
const DSW_SECOND_ALIAS = `${DSW_ALIAS}-2`;
const DSW_SERVER_URL = INTEGRATION_MODE === "real" ? env.dswServerUrl : `${env.mockBackendBaseUrl}${DSW_MOCK_PATH}`;
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
    for (const alias of [DSW_ALIAS, DSW_SECOND_ALIAS]) {
      await pageApps.setEnabledWithMultiConnection("DSW / FAIR Wizard", {
        aliasFieldLabel: "Label",
        aliasValue: alias,
        serverUrl: DSW_SERVER_URL,
        apiKey: DSW_API_KEY,
        configuredFormAriaLabelPrefix: "Configured DSW with label",
      });
    }
  });

  test("As a user, I can import a DSW project and keep using the Gallery", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    await pageGallery.openCreateMenu();
    await expect(pageGallery.dswImportMenuItems()).toHaveCount(2);

    const dialog = await pageGallery.clickDSWImport(DSW_ALIAS);
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();
    await expect(componentToasts.byVariant("success", planName)).toBeVisible();

    await dialog.dismiss();

    // Include hidden elements to detect a Create menu that failed to unmount.
    await expect(pageGallery.sidebar.createButton).toBeVisible();
    await expect(pageGallery.mountedCreateMenu()).toHaveCount(0);

    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(planName)).toBeVisible();
  });
});

test.describe("DSW / FAIR Wizard integration with a pre-5.0.0 server [mock]", { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "only the mock backend provides a pre-5.0.0 DSW server");

  const DSW_V4_ALIAS = "mock-v4";

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dsw.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithMultiConnection("DSW / FAIR Wizard", {
      aliasFieldLabel: "Label",
      aliasValue: DSW_V4_ALIAS,
      serverUrl: `${env.mockBackendBaseUrl}${DSW_V4_MOCK_PATH}`,
      apiKey: "mock-dsw-token",
      configuredFormAriaLabelPrefix: "Configured DSW with label",
    });
  });

  // The legacy mock only serves /wizard-api/, so this import succeeds only if RSpace picks it.
  test("As a user, I can import a project from a pre-5.0.0 DSW server", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    await pageGallery.openCreateMenu();
    const dialog = await pageGallery.clickDSWImport(DSW_V4_ALIAS);
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();
    await expect(componentToasts.byVariant("success", planName)).toBeVisible();

    await dialog.dismiss();
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(planName)).toBeVisible();
  });
});
