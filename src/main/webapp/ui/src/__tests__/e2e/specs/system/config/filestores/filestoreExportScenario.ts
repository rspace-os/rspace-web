import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { FilestoreBackend } from "@/__tests__/e2e/fixtures/flows/environment/filestoreBackends";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";
import { listZipEntries, readZipEntryText } from "@/__tests__/e2e/zipArchive";

export function describeFilestoreExport(backend: FilestoreBackend): void {
  test.describe(`${backend} filestore export`, { tag: tags.SYSTEM }, () => {
    test.use({ filestoreBackend: backend });

    test("As a user, I can export a document with filestore links, and only the files my filters allow are archived", async ({
      flowFilestore,
      clientDocuments,
      pageWorkspace,
      componentNotifications,
      pageExportReport,
    }) => {
      const { seededFolderPath, credentials, createFilestore, filestoreLinksHtml, scanPath, dataset } = flowFilestore;
      const { excludedTypes, excludedFile, archivedFiles, folders } = dataset.export;
      const filestore = await createFilestore(seededFolderPath);
      const documentName = alphaNumericUnique("e2eFilestoreExport");
      const linkedPaths = [excludedFile.path, ...archivedFiles.map((file) => file.path), ...folders.map((f) => f.path)];
      const created = await clientDocuments.create({
        name: documentName,
        fields: [{ content: filestoreLinksHtml(filestore, linkedPaths) }],
      });

      const document = await pageWorkspace.openDocument(created.id);
      const wizard = await document.openExport();
      await wizard.selectFormat("xml");
      await wizard.setIncludeFilestoreLinks(true);
      await wizard.next();
      const filestoreStep = await wizard.nextToFilestoreLinks();
      await filestoreStep.loginToFileSystems(credentials);
      await filestoreStep.excludeFileTypes(excludedTypes);

      await filestoreStep.scan();
      await expect(filestoreStep.skippedFile(scanPath(excludedFile.path))).toContainText(excludedFile.reason);
      for (const file of archivedFiles) {
        await expect(filestoreStep.includedFile(scanPath(file.path))).toBeVisible();
      }
      for (const folder of folders) {
        await expect(filestoreStep.includedFile(scanPath(folder.path))).toBeVisible();
      }
      await filestoreStep.closeScanResults();

      const notificationsBefore = await componentNotifications.getBadgeCount();
      await filestoreStep.export();
      await expect
        .poll(() => componentNotifications.getBadgeCount(), { timeout: 60_000 })
        .toBeGreaterThan(notificationsBefore);
      await pageWorkspace.open();
      await componentNotifications.open();
      const reportHref = await componentNotifications.getExportReportHref(documentName);
      const archive = await componentNotifications.downloadExportArchive(documentName);

      await pageExportReport.openAt(reportHref);
      await expect(pageExportReport.addedToArchive(excludedFile.path)).toHaveText("no");
      await expect(pageExportReport.error(excludedFile.path)).toContainText(excludedFile.reason);
      for (const file of archivedFiles) {
        await expect(pageExportReport.addedToArchive(file.path)).toHaveText("yes");
      }
      for (const folder of folders) {
        await expect(pageExportReport.addedToArchive(folder.path)).toHaveText("yes");
        await expect(pageExportReport.folderSummary(folder.path)).toContainText(
          `${folder.skippedEntry} (file skipped (${folder.reason}))`,
        );
      }

      // The report only says what the server intended; the archive shows which files it really holds.
      const entries = listZipEntries(archive);
      expect(entries).not.toContainEqual(expect.stringContaining(excludedFile.path.slice(1)));
      for (const folder of folders) {
        expect(entries).not.toContainEqual(expect.stringContaining(`${folder.path.slice(1)}${folder.skippedEntry}`));
      }
      for (const file of archivedFiles) {
        expect(entries).toContainEqual(expect.stringContaining(file.path.slice(1)));
      }
      for (const { path, content } of archivedFiles.filter((file) => file.content !== undefined)) {
        expect(readZipEntryText(archive, (entry) => entry.endsWith(path.slice(1))).trim()).toBe(content);
      }
    });
  });
}
