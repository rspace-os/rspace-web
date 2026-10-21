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
  test.describe.configure({ timeout: 120_000 });
  test("As a user, I can preview a DOCX as a generated PDF", async ({ clientFiles, pageGallery, page }) => {
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

  test("As a user, I can preview Office and text formats and reuse the cached PDF", async ({ clientFiles, page }) => {
    test.setTimeout(240_000);
    for (const [file, mimeType] of [
      ["simpleExcel.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"],
      ["ODP.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"],
      ["ODT.odt", "application/vnd.oasis.opendocument.text"],
      ["testTxt.txt", "text/plain"],
      ["word2rspace/powerpaste/README.md", "text/markdown"],
      ["csv.csv", "text/csv"],
    ]) {
      const uploaded = await clientFiles.uploadFile({
        name: `${uniqueName("e2e-preview")}.${file.split(".").at(-1)}`,
        mimeType,
        buffer: readFileSync(resolve(process.cwd(), "../../../test/resources/TestResources", file)),
      });
      const url = `/Streamfile/ajax/convert/${uploaded.id}?outputFormat=pdf`;
      const first = await page.request.get(url);
      expect(first.ok()).toBe(true);
      const converted = (await first.json()) as { data: string };
      const second = await page.request.get(url);
      expect(second.ok()).toBe(true);
      expect(await second.json()).toEqual(await first.json());
      const pdf = await page.request.get(
        `/Streamfile/direct/${uploaded.id}?fileName=${encodeURIComponent(converted.data)}`,
      );
      expect(pdf.ok()).toBe(true);
      expect(pdf.headers()["content-type"]).toContain("application/pdf");
      expect((await pdf.body()).subarray(0, 5).toString("ascii")).toBe("%PDF-");
    }
  });

  for (const extension of ["docx", "odt", "ott"] as const) {
    test(`As a user, I can import ${extension.toUpperCase()} content and export a valid DOCX`, async ({
      clientDocuments,
      pageDocument,
      componentExportWizard,
      componentNotifications,
      componentToasts,
      page,
    }) => {
      const imported = await clientDocuments.importWord({
        name: `${uniqueName("e2e-jodconverter-import")}.${extension}`,
        mimeType:
          extension === "docx"
            ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            : extension === "odt"
              ? "application/vnd.oasis.opendocument.text"
              : "application/vnd.oasis.opendocument.text-template",
        buffer: readFileSync(
          resolve(
            process.cwd(),
            `../../../test/resources/TestResources/word2rspace/powerpaste/PowerPasteTesting_RSpace.${extension}`,
          ),
        ),
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
  }
});
