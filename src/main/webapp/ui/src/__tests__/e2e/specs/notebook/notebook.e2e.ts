import { expect } from "@playwright/test";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique, TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Notebook CRUD", () => {
  test("As a user, I can create a notebook and add, rename, and delete entries", async ({
    pageWorkspace,
    pageNotebook,
  }) => {
    const notebookName = uniqueName("e2e-nb-crud");

    await test.step("Given I create a notebook", async () => {
      await pageWorkspace.open();
      await pageWorkspace.createNotebook(notebookName);
    });

    await test.step("Then it starts empty", async () => {
      await expect(pageNotebook.emptyState).toBeVisible();
    });

    const editor1 = await test.step("When I add a first entry and give it a name", async () => {
      const editor = await pageNotebook.addEntry();
      await editor.header.rename("Entry 1");
      return editor;
    });

    await test.step("And save it", async () => {
      await editor1.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the notebook shows one entry named 'Entry 1'", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
    });

    await test.step("When I add a second entry", async () => {
      const editor2 = await pageNotebook.addEntry();
      await editor2.header.rename("Entry 2");
      await editor2.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the ribbon shows both entries", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 2")).toBeVisible();
    });

    await test.step("When I delete the second entry", async () => {
      await pageNotebook.selectEntry("Entry 2");
      await pageNotebook.deleteEntry();
    });

    await test.step("Then only the first entry remains", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 2")).toBeHidden();
    });
  });

  test("As a user, I can edit an existing entry's content directly from the notebook view", async ({
    pageWorkspace,
    pageNotebook,
    clientFolders,
    clientDocuments,
  }) => {
    const notebookName = uniqueName("e2e-nb-inline-edit");
    const content = uniqueName("e2e-nb-content");

    await test.step("Given a notebook with one entry exists", async () => {
      const notebook = await clientFolders.create({ name: notebookName, notebook: true });
      await clientDocuments.create({ name: "Entry 1", parentFolderId: notebook.id, fields: [{ content: "original" }] });
    });

    await test.step("When I open the notebook and edit the entry inline", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(notebookName);
      await pageNotebook.isLoaded();
      const editor = await pageNotebook.enterEditMode();
      const field = await editor.getField("", 0);
      await field.fill(content);
      await editor.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the updated content is visible", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(notebookName);
      await pageNotebook.isLoaded();
      await pageNotebook.showAllEntries();
      await pageNotebook.selectEntry("Entry 1");

      await expect(pageNotebook.entryContent).toContainText(content);
    });
  });

  test("As a user, searching within a notebook journal finds the entry containing the search text", async ({
    pageWorkspace,
    pageNotebook,
    clientFolders,
    clientDocuments,
  }) => {
    const notebookName = uniqueName("e2e-nb-search");
    const text1 = alphaNumericUnique("e2eNbSearchText1");
    const text2 = alphaNumericUnique("e2eNbSearchText2");

    await test.step("Given a notebook with entries containing distinct content exists", async () => {
      const notebook = await clientFolders.create({ name: notebookName, notebook: true });
      await clientDocuments.create({ name: "Entry 1", parentFolderId: notebook.id, fields: [{ content: text1 }] });
      await clientDocuments.create({ name: "Entry 2", parentFolderId: notebook.id, fields: [{ content: text2 }] });
    });

    await test.step("When I open the notebook and search for the second entry's content", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(notebookName);
      await pageNotebook.isLoaded();
      await expect(pageNotebook.entryStrip.searchInput).toBeVisible();
      await pageNotebook.entryStrip.search(text2);
    });

    await test.step("Then only the matching entry appears in the ribbon", async () => {
      await expect(async () => {
        expect(await pageNotebook.isEntryVisibleInRibbon("Entry 2")).toBe(true);
        expect(await pageNotebook.isEntryVisibleInRibbon("Entry 1")).toBe(false);
      }).toPass();
    });
  });

  test("As a user, I can insert two gallery images into a notebook entry", async ({
    pageWorkspace,
    pageNotebook,
    clientFolders,
    clientDocuments,
    clientFiles,
  }) => {
    const notebookName = uniqueName("e2e-nb-images");
    const image1 = { name: `${uniqueName("e2e-nb-image1")}.png`, mimeType: "image/png", buffer: TINY_PNG };
    const image2 = { name: `${uniqueName("e2e-nb-image2")}.png`, mimeType: "image/png", buffer: TINY_PNG };

    const uploadedImages = await test.step("Given a notebook entry and two gallery images exist", async () => {
      const notebook = await clientFolders.create({ name: notebookName, notebook: true });
      await clientDocuments.create({ name: "Entry 1", parentFolderId: notebook.id, fields: [{ content: "text" }] });
      const uploadedImage1 = await clientFiles.uploadFile(image1);
      const uploadedImage2 = await clientFiles.uploadFile(image2);
      return [uploadedImage1, uploadedImage2];
    });

    await test.step("When I open the entry and insert both images from the gallery", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(notebookName);
      await pageNotebook.isLoaded();
      const editor = await pageNotebook.enterEditMode();
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Images");
      await picker.openFolder("Api Inbox");
      await picker.selectItems([image1.name, image2.name]);
      await picker.add();

      await test.step("Then the two uploaded images are inserted", async () => {
        const field = await editor.getField("", 0);
        const expectedSourceIds = uploadedImages.map(({ id }) => String(id)).sort();
        await expect.poll(async () => (await field.getImageSourceIds()).sort()).toEqual(expectedSourceIds);
      });
    });
  });

  test("As a user, I can use Save & New, Save & Clone, and Save as Template from within a notebook entry", async ({
    pageWorkspace,
    pageNotebook,
  }) => {
    const notebookName = uniqueName("e2e-nb-save-variants");
    const templateName = uniqueName("e2e-nb-save-variants-template");
    const entry1Content = uniqueName("e2e-nb-save-variants-content");

    await test.step("Given I create a notebook and name its first entry with some content", async () => {
      await pageWorkspace.open();
      await pageWorkspace.createNotebook(notebookName);
      const editor = await pageNotebook.addEntry();
      await editor.header.rename("Entry 1");
      const field = await editor.getField("", 0);
      await field.fill(entry1Content);

      await test.step("When I use Save & New", async () => {
        const editor2 = await editor.saveAndNew();
        await editor2.header.rename("Entry 2");
        await editor2.editToolbar.saveAndClose();
        await pageNotebook.isLoaded();
      });
    });

    await test.step("Then both entries exist in the notebook", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 2")).toBeVisible();
    });

    await test.step("When I open Entry 1 and use Save & Clone", async () => {
      await pageNotebook.selectEntry("Entry 1");
      const editor = await pageNotebook.enterEditMode();
      const cloned = await editor.saveAndClone();

      await test.step("Then a clone named 'Entry 1-copy' is now open for editing, with the original's content", async () => {
        await expect.poll(() => cloned.header.getName()).toBe("Entry 1-copy");
        const clonedField = await cloned.getField("", 0);
        expect(await clonedField.getText()).toBe(entry1Content);
      });

      await test.step("When I save the clone as a template", async () => {
        await cloned.editToolbar.saveAsTemplate(templateName);
      });

      await cloned.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the notebook now has three entries, including the clone", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 2")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 1-copy")).toBeVisible();
    });

    await test.step("And the new template appears in the workspace Templates folder", async () => {
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(templateName);
      await expect(pageWorkspace.table.row(templateName)).toBeVisible();
    });
  });

  test("As a user, I can create notebook entries from an existing form and from a saved template", async ({
    pageWorkspace,
    pageNotebook,
  }) => {
    const notebookName = uniqueName("e2e-nb-create-variants");
    const templateName = uniqueName("e2e-nb-create-variants-template");
    const templatedEntryName = "Entry from template";
    const templateContent = alphaNumericUnique("e2eNbCreateVariantsTemplateContent");

    await test.step("Given a saved document template with distinctive content exists", async () => {
      await pageWorkspace.open();
      const doc = await pageWorkspace.createBasicDocument();
      const field = await doc.getField("", 0);
      await field.fill(templateContent);
      await doc.editToolbar.save();
      await doc.editToolbar.saveAsTemplate(templateName);
      await pageWorkspace.open();
    });

    await test.step("Given I create a notebook", async () => {
      await pageWorkspace.createNotebook(notebookName);
    });

    const formEntry = await test.step("When I create an entry from the 'Experiment' form", async () => {
      return pageNotebook.createFromForm("Experiment");
    });

    await test.step("Then the new entry has the Experiment form's Method field, proving the form's structure was applied", async () => {
      await expect(await formEntry.getFieldViewContent("Method")).toBeVisible();
    });

    await test.step("And I close it", async () => {
      await formEntry.close();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the notebook shows the new entry", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Untitled document")).toBeVisible();
    });

    await test.step("When I create an entry from the saved template", async () => {
      const editor = await pageNotebook.createFromTemplate(templateName, templatedEntryName);
      await editor.editToolbar.saveAndClose();
      await pageNotebook.isLoaded();
    });

    await test.step("Then the notebook shows both entries", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.entryThumbnail("Untitled document")).toBeVisible();
      await expect(pageNotebook.entryThumbnail(templatedEntryName)).toBeVisible();
    });

    await test.step("And reopening the templated entry shows the template's seeded content, proving it wasn't created blank", async () => {
      await pageNotebook.selectEntry(templatedEntryName);
      await expect(pageNotebook.entryContent).toContainText(templateContent);
    });
  });

  test("As a user, a notebook's workspace actions and content survive a rename and reopening via its icon", async ({
    pageWorkspace,
    pageNotebook,
  }) => {
    const notebookName = uniqueName("e2e-nb-rename");
    const renamedNotebookName = uniqueName("e2e-nb-renamed");

    await test.step("Given a notebook with two entries exists", async () => {
      await pageWorkspace.open();
      await pageWorkspace.createNotebook(notebookName);
      for (const name of ["Entry 1", "Entry 2"]) {
        const editor = await pageNotebook.addEntry();
        await editor.header.rename(name);
        await editor.editToolbar.saveAndClose();
        await pageNotebook.isLoaded();
      }
    });

    await test.step("Then the notebook-applicable workspace actions are available", async () => {
      await pageWorkspace.open();
      await pageWorkspace.table.selectRecord(notebookName);
      for (const action of [
        "Duplicate",
        "Move",
        "Rename",
        "Delete",
        "Export",
        "Add to Favorites",
        "Add/Remove Tags",
      ] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(true);
      }
      for (const action of ["CSV", "Revisions"] as const) {
        expect(await pageWorkspace.selectionBar.isActionVisible(action)).toBe(false);
      }
      await pageWorkspace.table.deselectRecord(notebookName);
    });

    await test.step("When I rename the notebook and reopen it via its icon", async () => {
      await pageWorkspace.table.selectRecord(notebookName);
      await pageWorkspace.selectionBar.rename(renamedNotebookName);
      await pageWorkspace.open();
      await pageWorkspace.table.openNotebook(renamedNotebookName);
      await pageNotebook.isLoaded();
    });

    await test.step("Then the notebook still shows both entries under its new name", async () => {
      await pageNotebook.showAllEntries();
      await expect(pageNotebook.ribbon).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 1")).toBeVisible();
      await expect(pageNotebook.entryThumbnail("Entry 2")).toBeVisible();
    });
  });
});

dynamicUserTest.describe("Notebook export and persisted view state", () => {
  dynamicUserTest(
    "As a user, the all-entries ribbon visibility setting persists across reopening the notebook",
    async ({ pageWorkspace, pageNotebook, clientFolders, clientDocuments }) => {
      const notebookName = uniqueName("e2e-nb-ribbon");

      await dynamicUserTest.step("Given a notebook with three entries exists", async () => {
        const notebook = await clientFolders.create({ name: notebookName, notebook: true });
        for (const name of ["Entry 1", "Entry 2", "Entry 3"]) {
          await clientDocuments.create({ name, parentFolderId: notebook.id, fields: [{ content: name }] });
        }
      });

      await dynamicUserTest.step("When I open the notebook and hide the ribbon", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openNotebook(notebookName);
        await pageNotebook.isLoaded();
        await pageNotebook.showAllEntries();
        await expect(pageNotebook.ribbon).toBeVisible();
        await pageNotebook.hideAllEntries();
        await expect(pageNotebook.ribbon).toBeHidden();
      });

      await dynamicUserTest.step("Then the ribbon stays hidden after reopening the notebook", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openNotebook(notebookName);
        await pageNotebook.isLoaded();
        await expect(pageNotebook.ribbon).toBeHidden();
      });

      await dynamicUserTest.step("When I show the ribbon again", async () => {
        await pageNotebook.showAllEntries();
        await expect(pageNotebook.ribbon).toBeVisible();
      });

      await dynamicUserTest.step("Then it stays visible after reopening the notebook", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openNotebook(notebookName);
        await pageNotebook.isLoaded();
        await expect(pageNotebook.ribbon).toBeVisible();
      });
    },
  );

  dynamicUserTest(
    "As a user, I can export a whole notebook to PDF",
    async ({
      pageWorkspace,
      pageGallery,
      componentExportWizard,
      componentNotifications,
      clientFolders,
      clientDocuments,
    }) => {
      const notebookName = uniqueName("e2e-nb-export");
      const entry1Content = alphaNumericUnique("e2eNbExportEntry1Content");
      const entry2Content = alphaNumericUnique("e2eNbExportEntry2Content");

      await dynamicUserTest.step("Given a notebook with entries exists", async () => {
        const notebook = await clientFolders.create({ name: notebookName, notebook: true });
        await clientDocuments.create({
          name: "Entry 1",
          parentFolderId: notebook.id,
          fields: [{ content: entry1Content }],
        });
        await clientDocuments.create({
          name: "Entry 2",
          parentFolderId: notebook.id,
          fields: [{ content: entry2Content }],
        });
      });

      await dynamicUserTest.step("And Gallery > Exports starts empty for this fresh account", async () => {
        await pageGallery.openInSection("Exports");
        expect(await pageGallery.itemsCount()).toBe(0);
      });

      await dynamicUserTest.step("When I export the whole notebook as a PDF", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.selectRecord(notebookName);
        await pageWorkspace.selectionBar.clickAction("Export");
        await componentExportWizard.waitForOpen();
        await componentExportWizard.selectFormat("pdf");
        await componentExportWizard.next();
        await componentExportWizard.submit();
      });

      await dynamicUserTest.step(
        "Then the export completes and the PDF appears in Gallery > Exports, containing both entries' content",
        async () => {
          await componentNotifications.waitForBadgeCountInUI(1);
          // The export wizard pre-fills "File name" with the exported record's own name.
          const fileName = `${notebookName}.pdf`;
          await pageGallery.openInSection("Exports");
          await pageGallery.waitForFile(fileName);
          const text = await pageGallery.downloadAndExtractText(fileName);
          expect(text).toContain(entry1Content);
          expect(text).toContain(entry2Content);
        },
      );
    },
  );

  dynamicUserTest(
    "As a user, I can export a single entry to PDF from within the notebook view",
    async ({
      pageWorkspace,
      pageNotebook,
      pageGallery,
      componentExportWizard,
      componentNotifications,
      clientFolders,
      clientDocuments,
    }) => {
      dynamicUserTest.setTimeout(90_000);
      const notebookName = uniqueName("e2e-nb-export-entry");
      const entryName = uniqueName("e2e-nb-export-entry1");
      const otherEntryName = uniqueName("e2e-nb-export-entry2");
      const selectedContent = alphaNumericUnique("e2eNbExportSelectedContent");
      const otherContent = alphaNumericUnique("e2eNbExportOtherContent");

      await dynamicUserTest.step("Given a notebook with two entries exists", async () => {
        const notebook = await clientFolders.create({ name: notebookName, notebook: true });
        await clientDocuments.create({
          name: entryName,
          parentFolderId: notebook.id,
          fields: [{ content: selectedContent }],
        });
        await clientDocuments.create({
          name: otherEntryName,
          parentFolderId: notebook.id,
          fields: [{ content: otherContent }],
        });
      });

      await dynamicUserTest.step("When I export just the selected entry from within the notebook", async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.openNotebook(notebookName);
        await pageNotebook.isLoaded();
        await pageNotebook.showAllEntries();
        await pageNotebook.selectEntry(entryName);

        // Re-selecting the active entry must also leave it ready for the export action.
        await pageNotebook.selectEntry(entryName);

        await expect(async () => {
          await pageNotebook.toolbar.actions.exportButton.click();
          await componentExportWizard.waitForOpen();
        }).toPass({ timeout: 15_000 });
        await componentExportWizard.selectFormat("pdf");
        await componentExportWizard.next();

        await componentExportWizard.fillFileName(entryName);
        await componentExportWizard.submit();
      });

      await dynamicUserTest.step(
        "Then the export completes and the PDF appears in Gallery > Exports, containing only the selected entry's content",
        async () => {
          await componentNotifications.waitForBadgeCountInUI(1);
          const fileName = `${entryName}.pdf`;
          await pageGallery.openInSection("Exports");
          await pageGallery.waitForFile(fileName);
          const text = await pageGallery.downloadAndExtractText(fileName);
          expect(text).toContain(selectedContent);
          expect(text).not.toContain(otherContent);
        },
      );
    },
  );
});
