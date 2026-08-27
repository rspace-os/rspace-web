import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_ORCID_ID } from "./mock";

const INTEGRATION_MODE = env.integrationMode;
const REAL_ORCID_ID = "0009-0000-2449-2204";

async function connectToOrcidViaLogin(popup: Page): Promise<void> {
  await popup.getByRole("textbox", { name: "Email or ORCID iD" }).fill(env.orcidUsername);
  await popup.getByRole("textbox", { name: "Password" }).fill(env.orcidPassword);
  await popup.getByRole("button", { name: "Sign in to ORCID" }).click();
}

test.describe(`ORCID integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.orcidUsername && env.orcidPassword),
    "real mode needs RSPACE_DEV_ORCID_EMAIL/RSPACE_DEV_ORCID_PASS in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("orcid.available", "ALLOWED");
  });

  test("As a user, I can set my ORCID iD on my profile", async ({ page, pageOrcidProfile }) => {
    if (INTEGRATION_MODE !== "real") {
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
    }

    await pageOrcidProfile.open();
    await expect(pageOrcidProfile.setOrcidIdLink).toBeVisible();

    await pageOrcidProfile.connectOrcid({
      onExternalAuth: INTEGRATION_MODE === "real" ? connectToOrcidViaLogin : undefined,
    });

    const orcidId = INTEGRATION_MODE === "real" ? REAL_ORCID_ID : MOCK_ORCID_ID;
    await expect(pageOrcidProfile.connectedOrcidLink).toContainText(orcidId);
  });
});
