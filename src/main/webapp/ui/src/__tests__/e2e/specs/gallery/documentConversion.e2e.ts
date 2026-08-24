import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const DOCX = readFileSync(
  resolve(process.cwd(), "../../../test/resources/TestResources/PowerPasteTesting_RSpace.docx"),
);

test.describe("Gotenberg and JODConverter", { tag: tags.GALLERY }, () => {
  test("previews a DOCX as a generated PDF", async ({ clientFiles, pageGallery, page }) => {
    const name = `${uniqueName("e2e-gotenberg-preview")}.docx`;
    const uploaded = await clientFiles.uploadFile({
      name,
      mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      buffer: DOCX,
    });
    await pageGallery.openFile(uploaded.id);
    await pageGallery.infoPanel.waitUntilSelected(name);

    const pdfResponse = page.waitForResponse(
      (response) => response.url().includes("/Streamfile/direct/") && response.status() === 200,
    );
    await pageGallery.infoPanel.viewButton.click();

    await expect(page.getByRole("dialog", { name: "PDF Preview" })).toBeVisible();
    const response = await pdfResponse;
    expect(response.headers()["content-type"]).toContain("application/pdf");
    expect((await response.body()).subarray(0, 5).toString("ascii")).toBe("%PDF-");
    // PDF.js renders pages as unnamed canvases, so there is no semantic locator to use here.
    await expect(page.getByRole("dialog", { name: "PDF Preview" }).locator("canvas").first()).toBeVisible();
  });

  test("imports DOCX content and exports a valid DOCX", async ({
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
    page,
  }) => {
    const imported = await clientDocuments.importWord({
      name: `${uniqueName("e2e-jodconverter-import")}.docx`,
      mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      buffer: DOCX,
    });

    try {
      await page.goto(`/workspace/editor/structuredDocument/${imported.id}`);
      await pageDocument.isLoaded();
      // The legacy JSP editor exposes generated field IDs but no role or accessible label.
      const importedField = page.locator('[id^="div_rtf_"]:visible').filter({
        hasText: /Testing PowerPaste by Cutting and Pasting/,
      });
      await expect(importedField).toBeVisible();
      await expect(importedField.getByRole("img", { name: /^image e2e-jodconverter-import-/ })).toHaveCount(3);

      const baselineNotifications = await componentNotifications.getBadgeCount();
      await pageDocument.toolbar.actions.exportButton.click();
      await componentExportWizard.waitForOpen();
      await componentExportWizard.selectFormat("doc");
      await componentExportWizard.next();
      await componentExportWizard.submit();
      await expect(
        componentToasts.byVariant("success", "Your export generation request has been submitted"),
      ).toBeVisible();
      await expect
        .poll(() => componentNotifications.getBadgeCount(), { timeout: 90_000, intervals: [2_000] })
        .toBeGreaterThanOrEqual(baselineNotifications + 1);

      await componentNotifications.open();
      // The legacy notifications table has neither row labels nor stable row IDs.
      const notification = componentNotifications.root.locator("tr.notificationRow").filter({
        hasText: `Your export [${imported.name}] is now available`,
      });
      const galleryHref = await notification.getByRole("link").getAttribute("href");
      expect(galleryHref).not.toBeNull();
      const exportId = new URL(galleryHref as string, page.url()).pathname.split("/").at(-1);
      expect(exportId).toMatch(/^\d+$/);

      const download = await page.request.get(`/Streamfile/${exportId}`);
      expect(download.ok()).toBe(true);
      expect(download.headers()["content-type"]).toContain(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      );
      const docx = await download.body();
      expect(docx.subarray(0, 2).toString("ascii")).toBe("PK");
      expect(docx.includes(Buffer.from("word/document.xml"))).toBe(true);
    } finally {
      await clientDocuments.deleteById(imported.id);
    }
  });
});
