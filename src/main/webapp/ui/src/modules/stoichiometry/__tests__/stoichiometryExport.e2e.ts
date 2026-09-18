import { expect, type Page } from "@playwright/test";
import type { ExportFormat, ExportWizardComponent } from "@/__tests__/e2e/components/shared/ExportWizardComponent";
import type { NotificationsDialogComponent } from "@/__tests__/e2e/components/shared/NotificationsDialogComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { listZipEntries, readZipEntryText } from "@/__tests__/e2e/zipArchive";
import { REACTION_CDXML, REACTION_FILE_NAME } from "./stoichiometryTestHelpers";

/**
 * Submits the export wizard for the currently-viewed document and waits for its notification.
 *
 * The view-mode Reaction Table widget has its own grid-export "Export" button, which
 * collides by accessible name with the document toolbar's own; `#exportDocument` is the
 * toolbar's stable legacy id and disambiguates the two.
 */
async function exportCurrentDocument(
  page: Page,
  componentExportWizard: ExportWizardComponent,
  componentNotifications: NotificationsDialogComponent,
  format: ExportFormat,
  description: string,
): Promise<void> {
  const baselineCount = await componentNotifications.getBadgeCount();
  await page.locator("#exportDocument").click();
  await componentExportWizard.waitForOpen();
  await componentExportWizard.selectFormat(format);
  await componentExportWizard.next();
  if (format === "html") {
    await componentExportWizard.selectLinkedDocumentsDepth("none");
    await componentExportWizard.fillExportDescription(description);
  } else {
    await componentExportWizard.fillFileName(description);
  }
  await componentExportWizard.submit();

  await expect.poll(() => componentNotifications.getBadgeCount(), { timeout: 60_000 }).toBeGreaterThan(baselineCount);
}

/** Slices out one `<tr>...</tr>` row's markup by the molecule name in its first cell. */
function tableRowHtml(html: string, moleculeName: string): string {
  const marker = `>${moleculeName}</td>`;
  const start = html.indexOf(marker);
  if (start === -1) throw new Error(`tableRowHtml: "${moleculeName}" not found in exported HTML`);
  const end = html.indexOf("</tr>", start);
  return html.slice(start, end);
}

test.describe("Stoichiometry export (RSDEV-911)", { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
  });
  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, exporting a document with a stoichiometry table shows the reaction header, table title, and an Inventory column that links once a reagent is linked", async ({
    pageWorkspace,
    page,
    componentExportWizard,
    componentNotifications,
    clientInventory,
  }) => {
    const docName = uniqueName("e2e-stoich-export");
    const sample = await clientInventory.createSample({
      name: `e2e-stoich-export-sample-${Date.now()}`,
      newSampleSubSamplesCount: 1,
    });

    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    await docEditor.header.rename(docName);
    const picker = await docEditor.openGalleryPicker();
    await picker.goToSection("Chemistry");
    await picker.uploadFile(REACTION_CDXML, REACTION_FILE_NAME);
    await picker.selectItem(REACTION_FILE_NAME);
    await picker.add();

    const field = await docEditor.getField("New List of Materials");
    const stoichiometryDialog = await field.chemistry.openStoichiometryDialog();
    await stoichiometryDialog.calculate();
    const linkDialog = await stoichiometryDialog.openAddInventoryLinkDialog("Benzene");
    await linkDialog.search(sample.subSamples[0].name);
    await linkDialog.select(sample.subSamples[0].name);
    await linkDialog.choose();
    await stoichiometryDialog.editCell("Benzene", "actualAmount", "1");
    await stoichiometryDialog.saveChanges();
    await stoichiometryDialog.close();
    await docEditor.saveAndView();

    let html = "";
    await test.step("Export the document as HTML", async () => {
      const description = `${docName}-html`;
      await exportCurrentDocument(page, componentExportWizard, componentNotifications, "html", description);

      await componentNotifications.open();
      const downloadHref = await componentNotifications.getExportDownloadHref(description);
      await componentNotifications.close();

      const archiveResponse = await page.request.get(downloadHref);
      expect(archiveResponse.ok()).toBe(true);
      const buffer = await archiveResponse.body();
      const entries = listZipEntries(buffer);
      const docEntry = entries.find((entry) => entry.includes("/doc_") && entry.endsWith(".html"));
      if (!docEntry) throw new Error("Exported archive has no doc_*/doc_*.html entry");
      html = readZipEntryText(buffer, (name) => name === docEntry);
    });

    expect(html).toContain(`${REACTION_FILE_NAME}:`);
    expect(html).toContain(`Stoichiometry Information for ${REACTION_FILE_NAME}`);
    expect(tableRowHtml(html, "Benzene")).toContain(`globalId/${sample.subSamples[0].globalId}`);
    expect(tableRowHtml(html, "Cyclopentadiene")).toContain(">-<");
    expect(tableRowHtml(html, "Cyclohexane")).toContain(">-<");

    await test.step("Export the same document as PDF", async () => {
      const description = `${docName}-pdf`;
      await exportCurrentDocument(page, componentExportWizard, componentNotifications, "pdf", description);

      await componentNotifications.open();
      const notificationTexts = (await componentNotifications.getNotificationTexts()).join("\n");
      expect(notificationTexts).toContain(`Your export [${description}] is now available`);
      await componentNotifications.close();
    });
  });
});
