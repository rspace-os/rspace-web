import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { FilestoreBackend } from "@/__tests__/e2e/fixtures/flows/environment/filestoreBackends";
import { tags } from "@/__tests__/e2e/tags";

const BACKENDS: FilestoreBackend[] = ["s3", "sftp", "samba", "irods"];

for (const backend of BACKENDS) {
  test.describe(`${backend} filestore`, { tag: tags.SYSTEM }, () => {
    test.use({ filestoreBackend: backend });

    test("As a user, I can add a filestore for a folder of the sysadmin's file system and browse its files", async ({
      flowFilestore,
      pageGallery,
    }) => {
      const { fileSystemName, wizardFolder, wizardFolderEntries, credentials, uniqueFilestoreName } = flowFilestore;
      const name = uniqueFilestoreName();

      await pageGallery.openFilestoresSection();
      const dialog = await pageGallery.openAddFilestoreDialog();
      await dialog.addFilestore({ fileSystem: fileSystemName, folder: wizardFolder, name, credentials });
      await expect(pageGallery.fileCell(name)).toBeVisible();

      // The login made in the wizard still holds, so opening the filestore doesn't ask again.
      await pageGallery.openFilestore(name);
      for (const entry of wizardFolderEntries) {
        await expect(pageGallery.fileCell(entry)).toBeVisible();
      }
    });

    test("As a user, I can link a file and a folder from a filestore into a document, and each link shows where it is stored", async ({
      flowFilestore,
      pageWorkspace,
    }) => {
      const { seededFolderPath, dataset, linkDetails, linkedFolderPath, credentials, createFilestore } = flowFilestore;
      const { sampleFile, sampleFolders } = dataset;
      const filestore = await createFilestore(seededFolderPath);

      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Filestores");
      await picker.openFolder(filestore.name, credentials);
      await picker.selectFilestoreItems([sampleFile, ...sampleFolders]);
      await picker.add();
      const document = await editor.saveAndView();

      const expectedPaths = [
        { linked: sampleFile, originalPath: `${seededFolderPath}/${sampleFile}` },
        ...sampleFolders.map((folder) => ({ linked: folder, originalPath: linkedFolderPath(folder) })),
      ];
      for (const { linked, originalPath } of expectedPaths) {
        const details = await document.openFilestoreLink(linked);
        await expect(details.detail("Original path:")).toHaveText(originalPath);
        for (const { label, value } of linkDetails) {
          await expect(details.detail(label)).toHaveText(value);
        }
        await details.close();
      }
    });
  });
}
