import { expect, tags, test } from "@/__tests__/e2e/fixtures";
import { AppsPage } from "@/__tests__/e2e/pageObjects/AppsPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/WorkspacePage";

test.describe
  .serial(`PubChem integration ${tags.APPS}`, () => {
    test.beforeAll(async ({ flowSysadminConfig }) => {
      await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
    });

    test("After enabling Chemistry, the 'Insert PubChem Compound' toolbar button appears", async ({
      page,
      appUser,
      pageLogin,
    }) => {
      await test.step("Given I am logged in as a user", async () => {
        await pageLogin.open();
        await pageLogin.login(appUser.username, appUser.password);
        // Wait for login redirect before navigating further — login() submits the
        // form but returns immediately; goto() would race the POST and land on /login.
        await page.waitForURL((url) => !url.pathname.includes("/login"));
      });

      await test.step("When I enable Chemistry in my Apps settings", async () => {
        const apps = new AppsPage(page);
        await apps.setEnabled("Chemistry", true);
      });

      await test.step("Then the PubChem toolbar button is visible in a new document", async () => {
        await page.goto("/workspace");
        const workspace = new WorkspacePage(page);
        const docPage = await workspace.createBasicDocument();
        await expect(docPage.pubchemToolbarButton).toBeVisible();
      });
    });
  });
