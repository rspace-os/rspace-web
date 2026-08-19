import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

const MOCK_CROSSREF_FUNDER_ID = "https://doi.org/10.13039/100000001";

test.describe(`Dryad integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "is out of scope");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dryad.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect("Dryad");
  });

  test("As a user, I can export a document to Dryad and receive a completion notification", async ({
    page,
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
  }) => {
    await page.route("https://api.crossref.org/funders**", (route) =>
      route.fulfill({
        json: { message: { items: [{ id: MOCK_CROSSREF_FUNDER_ID, name: "Mock Funding Body" }] } },
      }),
    );

    const doc = await clientDocuments.create({ name: alphaNumericUnique("Dryad export") });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();

    const baselineNotificationCount = await componentNotifications.getBadgeCount();

    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();
    await componentExportWizard.selectFormat("pdf");
    await componentExportWizard.setExportToRepository(true);
    await componentExportWizard.next();

    await componentExportWizard.next();

    await componentExportWizard.selectRepository("Dryad");
    await componentExportWizard.fillTitle(`Title for ${doc.name}`);

    await componentExportWizard.fillAbstract(`Abstract for ${doc.name}`);
    await componentExportWizard.selectResearchDomain("Law");
    await componentExportWizard.selectRepositoryLicense("CC-0");
    await componentExportWizard.selectGrantingOrganization("Mock", "Mock Funding Body");

    const exportRequest = page.waitForRequest(
      (request) => request.url().endsWith("/export/ajax/export") && request.method() === "POST",
    );
    await componentExportWizard.submit();

    const exportRequestBody = (await exportRequest).postDataJSON() as {
      repositoryConfig?: { meta?: { otherProperties?: { funder?: string } } };
    };
    const submittedFunder = JSON.parse(exportRequestBody.repositoryConfig?.meta?.otherProperties?.funder ?? "{}") as {
      id?: string;
    };
    expect(submittedFunder.id).toBe(MOCK_CROSSREF_FUNDER_ID);

    await expect(
      componentToasts.byVariant("success", "Your export generation request has been submitted"),
    ).toBeVisible();

    await expect
      .poll(() => componentNotifications.getBadgeCount(), { timeout: 90_000, intervals: [2_000] })
      .toBeGreaterThanOrEqual(baselineNotificationCount + 2);

    await componentNotifications.open();
    const notificationTexts = (await componentNotifications.getNotificationTexts()).join("\n");
    expect(notificationTexts).toContain(`Your export [${doc.name}] is now available`);
    expect(notificationTexts).toContain("Your deposit to repository Dryad is complete");
    await componentNotifications.close();
  });
});
