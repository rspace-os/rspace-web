import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`DMPTool integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode is out of scope: connecting requires automating dmptool.org's real OAuth consent page",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dmptool.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("DMPTool");
  });

  test("As a user, I can import a DMPTool plan into the Gallery", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    const dialog = await pageGallery.openDMPToolImport();
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();

    await expect(componentToasts.byVariant("success", "was successfully imported")).toBeVisible();

    await pageGallery.open();
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(`${planName}.json`)).toBeVisible();
  });
});
