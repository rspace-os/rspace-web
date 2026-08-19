import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`DMP Assistant integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode is broken");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dmpassistant.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("DMP Assistant");
  });

  test("As a user, I can import a DMP Assistant plan into the Gallery", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    const dialog = await pageGallery.openDMPAssistantImport();
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();

    await expect(componentToasts.byVariant("success", "Successfully imported")).toBeVisible();

    await pageGallery.open();
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(`${planName}.json`)).toBeVisible();
  });
});
