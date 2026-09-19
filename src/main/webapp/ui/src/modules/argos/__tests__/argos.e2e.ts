import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_PLAN_LABEL } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe("Argos integration [mock]", { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode out of scope: no real Argos/DMP account exists for this suite, and this spec always imports the mock plan",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("argos.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("ARGOS", true);
  });

  test("As a user, I can import an Argos plan into the Gallery", async ({ pageGallery, componentToasts }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    const dialog = await pageGallery.openArgosImport();
    await dialog.selectPlan(MOCK_PLAN_LABEL);
    await dialog.clickImport();

    await expect(componentToasts.byVariant("success", "was successfully imported")).toBeVisible();

    await pageGallery.open();
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(`${MOCK_PLAN_LABEL}.json`)).toBeVisible();
  });
});
