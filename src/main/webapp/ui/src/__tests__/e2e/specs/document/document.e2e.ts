import { expect } from "@playwright/test";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

const MULTI_FIELD_FORM = "Experiment";

/** Deletes the named record and verifies it's gone from the listing and no longer searchable by content. */
async function deleteAndVerifyGone(pageWorkspace: WorkspacePage, docName: string, content: string): Promise<void> {
  await test.step("When I delete it", async () => {
    await pageWorkspace.open();
    await pageWorkspace.table.selectRecord(docName);
    await pageWorkspace.selectionBar.delete();
  });

  await test.step("Then it's gone from the workspace listing", async () => {
    await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
  });

  await test.step("And it's no longer searchable by its content", async () => {
    await pageWorkspace.searchBar.search(content);
    expect(await pageWorkspace.table.rowCount()).toBe(0);
  });
}

test.describe("Document CRUD", () => {
  test("As a user, I can rename a document", async ({ page, pageWorkspace }) => {
    const original = uniqueName("e2e-doc-original");
    const renamed = uniqueName("e2e-doc-renamed");

    const doc = await test.step("Given I create a document from the 'Experiment' form", async () => {
      await pageWorkspace.open();
      return pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
    });

    await test.step(`And name it "${original}"`, async () => {
      await doc.rename(original);
    });

    await test.step("When I rename it", async () => {
      await doc.rename(renamed);
    });

    await test.step("Then the new name appears in the header and the browser tab title", async () => {
      expect(await doc.header.getName()).toBe(renamed);
      await expect(page).toHaveTitle(`${renamed} | ResearchSpace`);
    });
  });

  test("As a user, I can delete a basic document and it's no longer searchable by its content", async ({
    pageWorkspace,
  }) => {
    const docName = uniqueName("e2e-doc-delete-basic");
    const content = alphaNumericUnique("e2edocdeletebasiccontent");

    await test.step("Given a basic document with content exists", async () => {
      await pageWorkspace.open();
      const doc = await pageWorkspace.createBasicDocument();
      await doc.header.rename(docName);
      const field = await doc.getField("", 0);
      await field.fill(content);
      await doc.editToolbar.saveAndClose();
    });

    await deleteAndVerifyGone(pageWorkspace, docName, content);
  });

  test("As a user, I can delete an experiment document and it's no longer searchable by its content", async ({
    pageWorkspace,
  }) => {
    const docName = uniqueName("e2e-doc-delete-experiment");
    const content = alphaNumericUnique("e2edocdeleteexperimentcontent");

    await test.step("Given an Experiment-form document with content exists", async () => {
      await pageWorkspace.open();
      const doc = await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
      await doc.rename(docName);

      const field = await doc.editField("Method", 0);
      await field.fill(content);
      await field.save();
    });

    await deleteAndVerifyGone(pageWorkspace, docName, content);
  });
});

dynamicUserTest.describe("Document CRUD", () => {
  dynamicUserTest(
    "As a user, I can export a document to PDF from the document view",
    async ({ pageWorkspace, pageGallery, componentExportWizard }) => {
      const docName = uniqueName("e2e-doc-export");

      const doc = await dynamicUserTest.step("Given an Experiment-form document exists", async () => {
        await pageWorkspace.open();
        const created = await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
        await created.rename(docName);
        return created;
      });

      await dynamicUserTest.step("When I export it as PDF from the document view", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openRecord(docName);
        await doc.isLoaded();

        await expect(async () => {
          await doc.toolbar.actions.exportButton.click();
          await componentExportWizard.waitForOpen();
        }).toPass({ timeout: 15_000 });
        await componentExportWizard.selectFormat("pdf");
        await componentExportWizard.next();
        await componentExportWizard.fillFileName(docName);
        await componentExportWizard.submit();
      });

      await dynamicUserTest.step("Then a new PDF appears in Gallery > Exports", async () => {
        await expect
          .poll(
            async () => {
              await pageGallery.openInSection("Exports");
              return pageGallery.fileCell(`${docName}.pdf`).isVisible();
            },
            { timeout: 45_000 },
          )
          .toBe(true);
      });
    },
  );
});
