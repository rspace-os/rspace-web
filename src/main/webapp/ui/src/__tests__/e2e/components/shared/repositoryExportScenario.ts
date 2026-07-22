import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import type { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { DocumentPage } from "@/__tests__/e2e/pageObjects/document/DocumentPage";
import { uniqueName } from "@/__tests__/e2e/testData";
import type { ExportWizardComponent } from "./ExportWizardComponent";
import type { NotificationsDialogComponent } from "./NotificationsDialogComponent";
import type { ToastsComponent } from "./ToastsComponent";

export async function expectRepositoryDepositCompletes(params: {
  repoDisplayName: string;
  selectRepository: (wizard: ExportWizardComponent) => Promise<void>;
  afterSelectRepository?: (wizard: ExportWizardComponent) => Promise<void>;
  clientDocuments: DocumentsClient;
  pageDocument: DocumentPage;
  componentExportWizard: ExportWizardComponent;
  componentNotifications: NotificationsDialogComponent;
  componentToasts: ToastsComponent;
  page: Page;
}): Promise<void> {
  const {
    repoDisplayName,
    selectRepository,
    afterSelectRepository,
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
    page,
  } = params;

  const docName = await test.step("Given I have a document to export", async () => {
    const doc = await clientDocuments.create({ name: uniqueName(`${repoDisplayName} export`) });
    await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
    await pageDocument.isLoaded();
    return doc.name;
  });

  const baselineNotificationCount = await test.step("And I note the current notification count", () =>
    componentNotifications.getBadgeCount());

  await test.step(`When I export it as a PDF to ${repoDisplayName}`, async () => {
    await pageDocument.toolbar.actions.exportButton.click();
    await componentExportWizard.waitForOpen();
    await componentExportWizard.selectFormat("pdf");
    await componentExportWizard.setExportToRepository(true);
    await componentExportWizard.next();

    await componentExportWizard.next();

    await selectRepository(componentExportWizard);
    await componentExportWizard.fillTitle(`Title for ${docName}`);
    await componentExportWizard.fillDescription(`Description for ${docName}`);
    await afterSelectRepository?.(componentExportWizard);
    await componentExportWizard.submit();
  });

  await test.step("Then the export is accepted for processing", async () => {
    await expect(
      componentToasts.byVariant("success", "Your export generation request has been submitted"),
    ).toBeVisible();
  });

  await test.step("And the notification count increases by at least 2 (export + deposit)", async () => {
    await expect
      .poll(() => componentNotifications.getBadgeCount(), { timeout: 90_000, intervals: [2_000] })
      .toBeGreaterThanOrEqual(baselineNotificationCount + 2);
  });

  await test.step("And the toolbar bell itself reflects the new count within its polling interval", async () => {
    const uiCount = await componentNotifications.waitForBadgeCountInUI(baselineNotificationCount + 2);
    expect(uiCount).toBeGreaterThanOrEqual(baselineNotificationCount + 2);
  });

  await test.step(`And the notifications confirm both the export and the ${repoDisplayName} deposit`, async () => {
    await componentNotifications.open();
    const notificationTexts = (await componentNotifications.getNotificationTexts()).join("\n");
    expect(notificationTexts).toContain(`Your export [${docName}] is now available`);
    expect(notificationTexts).toContain(`Your deposit to repository ${repoDisplayName} is complete.`);
    await componentNotifications.close();
  });
}
