import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_ORCID_ID } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`ORCID integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode out of scope by design: automating orcid.org's real login page is outside this suite's boundary",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("orcid.available", "ALLOWED");
  });

  test("As a user, I can set my ORCID iD on my profile", async ({ page, pageOrcidProfile }) => {
    // WebKit's route.fulfill() doesn't support redirect statuses (300-399) —
    // an actual 302 with a Location header works on chromium/firefox but
    // throws "Cannot fulfill with redirect status" on webkit. A same-behavior
    // client-side redirect via a tiny HTML/JS page works identically on all
    // three engines.
    await page.context().route("https://orcid.org/oauth/authorize**", async (route) => {
      const requestUrl = new URL(route.request().url());
      const redirectUri = requestUrl.searchParams.get("redirect_uri");
      if (!redirectUri) {
        await route.abort();
        return;
      }
      const target = new URL(redirectUri);
      target.searchParams.set("code", "mock-orcid-auth-code");
      await route.fulfill({
        contentType: "text/html",
        body: `<script>window.location.replace(${JSON.stringify(target.toString())});</script>`,
      });
    });

    await pageOrcidProfile.open();
    await expect(pageOrcidProfile.setOrcidIdLink).toBeVisible();

    await pageOrcidProfile.connectOrcid();

    await expect(pageOrcidProfile.connectedOrcidLink).toContainText(MOCK_ORCID_ID);
  });
});
