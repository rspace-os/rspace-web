import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;
const DSW_ALIAS = INTEGRATION_MODE === "real" ? "real" : "mock";
/*
 * PRT-1135 needs 2+ items under the Gallery create menu's "DMP Import"
 * section. A second connection to the same server is the cheapest way there
 * and matches the ticket's "a single integration like DSW with 2+ configured
 * server connections". Distinct aliases keep the menuitem locator unambiguous.
 */
const DSW_SECOND_ALIAS = `${DSW_ALIAS}-2`;
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

  /*
   * PRT-1135. With 2+ DMP import items in the create menu, dismissing the
   * import dialog after a successful import could leave the create menu itself
   * mounted: visually open, and with `aria-hidden="true"` leaked onto the rest
   * of the page, so nothing was reachable by role and only a reload recovered
   * it. Root cause is mui/material-ui#32286 (an exit transition that never
   * completes, so the Modal never unmounts and ModalManager never restores
   * aria-hidden), fixed upstream in @mui/material 9.3.1 by PR #48881.
   *
   * Assertions here are deliberately positive ("the page is reachable")
   * rather than negative ("the menu is hidden"): an aria-hidden menu root
   * cannot be matched by getByRole at all, so a hidden/absent assertion passes
   * *because* the bug is present.
   *
   * This test covers the real user flow -- two configured connections, a live
   * import, the dialog dismissed. The underlying mechanism is covered
   * separately and deterministically by components/DialogBoundary.spec.tsx,
   * which widens the exit window so the Suspense perturbation reliably lands
   * inside it; the race needs no production build to reproduce, only a wider
   * window than the ~300ms default.
   */
  test("As a user, closing the import dialog also closes the create menu and leaves the page reachable", async ({
    pageGallery,
    componentToasts,
    page,
  }) => {
    await pageGallery.open();
    await pageGallery.isLoaded();

    await pageGallery.openCreateMenu();
    await expect(pageGallery.dswImportMenuItems()).toHaveCount(2);

    const dialog = await pageGallery.clickDSWImport(DSW_ALIAS);
    const planName = await dialog.selectFirstPlan();
    await dialog.clickImport();
    await expect(componentToasts.byVariant("success", planName)).toBeVisible();

    await dialog.dismiss();

    // the create menu must go with the dialog, not linger behind it
    await expect(pageGallery.sidebar.createButton).toBeVisible();
    /*
     * A class locator, against the guidance, because that is the point: a
     * stuck Modal is precisely the thing no role query can see. MuiModal-root
     * is the only handle on "a modal is still mounted".
     */
    await expect.poll(() => page.locator(".MuiModal-root").count()).toBe(0);

    // and the sidebar must still be operable, which is what the leak broke
    await pageGallery.openSection("DMPs");
    await expect(pageGallery.fileCell(planName)).toBeVisible();
  });
});
