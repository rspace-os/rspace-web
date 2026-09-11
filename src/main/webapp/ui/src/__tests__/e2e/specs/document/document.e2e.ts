import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
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

  test("As a user, Save & Clone, Save & New, and Save & View behave differently than plain Save while editing a document", async ({
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
  }) => {
    const docName = uniqueName("e2e-doc-save-variants");
    const content1 = uniqueName("e2e-doc-save-variants-content1");
    const content2 = uniqueName("e2e-doc-save-variants-content2");

    await test.step("Given a basic document with saved content exists", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);
      const field = await editor.getField("", 0);
      await field.fill(content1);
      await editor.editToolbar.save();
    });

    await test.step("When I reopen it and use Save & Clone", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      await pageDocumentEditor.editToolbar.saveAndClone();
      await pageDocument.isLoaded();

      await expect.poll(() => pageDocument.header.getName()).toBe(`${docName}-copy`);
      const clonedContent = await pageDocument.getFieldViewContent("", 0);
      expect(await clonedContent.innerText()).toBe(content1);

      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      const clonedField = await pageDocumentEditor.getField("", 0);
      await clonedField.fill(content2);
      await pageDocumentEditor.editToolbar.saveAndClose();
    });

    await test.step("Then the original document keeps its own content, unaffected by the clone", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const content = await pageDocument.getFieldViewContent("", 0);
      expect(await content.innerText()).toBe(content1);
    });

    await test.step("When I use Save & New", async () => {
      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      await pageDocumentEditor.editToolbar.saveAndNew();
      await pageDocument.isLoaded();

      const freshContent = await pageDocument.getFieldViewContent("", 0);
      expect(await freshContent.innerText()).not.toContain(content1);
    });

    await test.step("When I use Save & View", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      const viewed = await pageDocumentEditor.saveAndView();

      const content = await viewed.getFieldViewContent("", 0);
      expect(await content.innerText()).toBe(content1);
    });
  });

  test("As a user, cancelling an edit reverts the document to its last-saved content and discards the unsaved change", async ({
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
  }) => {
    const docName = uniqueName("e2e-doc-cancel-edit");
    const firstContent = uniqueName("e2e-doc-cancel-edit-first");
    const otherContent = uniqueName("e2e-doc-cancel-edit-other");

    await test.step("Given a basic document with saved content exists", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);
      const field = await editor.getField("", 0);
      await field.fill(firstContent);
      await editor.editToolbar.saveAndClose();
    });

    await test.step("When I reopen it, change its content, and cancel and confirm", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      const field = await pageDocumentEditor.getField("", 0);
      await field.fill(otherContent);
      await pageDocumentEditor.editToolbar.cancel();
      await pageDocument.isLoaded();
    });

    await test.step("Then the field reverts to the last-saved content, not the discarded edit", async () => {
      const content = await pageDocument.getFieldViewContent("", 0);
      expect(await content.innerText()).toBe(firstContent);
    });

    await test.step("And cancelling but choosing to stay keeps the unsaved edit in place", async () => {
      await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      const field = await pageDocumentEditor.getField("", 0);
      await field.fill(otherContent);
      await pageDocumentEditor.editToolbar.cancelAndStay();
      expect(await field.getText()).toBe(otherContent);
    });
  });

  test("As a user, I can save and read back non-UTF characters and symbols in a field", async ({
    pageWorkspace,
    pageDocument,
  }) => {
    const docName = uniqueName("e2e-doc-utf-symbols");
    const text = "é ę ś à ö ñ 漢 こ جيد © ® € £ µ ¥ ∑";

    await test.step("Given a basic document with non-UTF/symbol content exists", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);
      const field = await editor.getField("", 0);
      await field.fill(text);
      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then reopening it shows the same content", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const content = await pageDocument.getFieldViewContent("", 0);
      expect((await content.innerText()).trim()).toBe(text);
    });
  });

  test("As a user, I can insert a video file from Gallery into a document", async ({ pageWorkspace, clientFiles }) => {
    const fileName = `${alphaNumericUnique("e2eDocVideo")}.mp4`;

    await test.step("Given a video file exists in Gallery", async () => {
      await clientFiles.uploadFile({ name: fileName, mimeType: "video/mp4", buffer: Buffer.from("e2e-fake-video") });
    });

    await test.step("When I insert it into a new document's field", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("", 0);
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Videos");
      await picker.openFolder("Api Inbox");
      await picker.selectItem(fileName);
      await picker.add();

      await test.step("Then the video appears as an attachment in the field", async () => {
        await expect(field.attachmentIcon).toBeVisible();
        await expect(field.attachmentName(fileName)).toBeVisible();
      });
    });
  });

  test("As a user, I can insert a document file from Gallery, and it stays non-editable alongside typed text", async ({
    pageWorkspace,
    clientFiles,
  }) => {
    const fileName = `${alphaNumericUnique("e2eDocAttachment")}.txt`;
    const surroundingText = alphaNumericUnique("TextOutsideAttachment");
    const file = await clientFiles.uploadFile({
      name: fileName,
      mimeType: "text/plain",
      buffer: Buffer.from("e2e file content"),
    });

    await test.step("When I insert it into a new document's field", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const field = await editor.getField("", 0);
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Documents");
      await picker.openFolder("Api Inbox");
      await picker.selectItem(fileName);
      await picker.add();
      await expect(field.attachmentName(fileName)).toHaveAttribute("href", `/Streamfile/${file.id}`);
      await expect(field.attachment(fileName)).toHaveAttribute("contenteditable", "false");

      await test.step("Then typing more text leaves the attachment in place", async () => {
        await field.typeAtEnd(surroundingText);
        await expect(field.attachmentIcon).toHaveCount(1);
        await expect.poll(() => field.getText()).toContain(surroundingText);
        await expect(field.attachment(fileName)).not.toContainText(surroundingText);
      });

      const saved = await editor.saveAndView();
      await saved.reload();
      const content = await saved.getFieldViewContent("", 0);
      await expect(content.getByText(fileName, { exact: true })).toBeVisible();
      await expect(content.getByRole("link", { name: "Download", exact: true })).toHaveAttribute(
        "href",
        `/Streamfile/${file.id}`,
      );
      await expect(content.getByText(surroundingText, { exact: true })).toBeVisible();
      const reopened = await saved.editField("", 0);
      await expect(reopened.attachment(fileName)).toHaveAttribute("contenteditable", "false");
      await expect(reopened.attachment(fileName)).not.toContainText(surroundingText);
    });
  });

  test("As a user, I can save a document as a template and create a new document from it", async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-doc-template-source");
    const templateName = uniqueName("e2e-doc-template");
    const newDocName = uniqueName("e2e-doc-from-template");
    const content = alphaNumericUnique("e2eDocTemplateContent");

    await test.step("Given a document with content, saved as a template", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);
      const field = await editor.getField("", 0);
      await field.fill(content);
      await editor.editToolbar.save();
      expect((await clientDocuments.getById(editor.getId())).fields[0].content).toContain(content);
      await editor.editToolbar.saveAsTemplate(templateName, ["Data"]);
      await editor.editToolbar.saveAndClose();
    });

    await test.step("When I create a new document from that template", async () => {
      await pageWorkspace.open();
      const created = await pageWorkspace.createDocumentFromTemplate(templateName, newDocName);

      await test.step("Then the included field retains the source document's saved content", async () => {
        const field = await created.getField("", 0);
        expect(await field.getText()).toContain(content);
        const saved = await created.saveAndView();
        await saved.reload();
        await expect(await saved.getFieldViewContent("", 0)).toContainText(content);
      });
    });
  });

  test("As a user, the TinyMCE File, Insert, and Format menus expose their expected actions", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const menus = {
      File: ["Save", "Print", "Find and replace", "Select all"],
      Insert: ["From Gallery", "External Link", "Internal Link", "Equation"],
      Format: ["Bold", "Italic", "Underline", "Clear formatting"],
    };
    for (const [menuName, actions] of Object.entries(menus)) {
      await test.step(`The ${menuName} menu exposes its expected actions`, async () => {
        const items = await field.menuItems(menuName);
        for (const action of actions) await expect(items.filter({ hasText: action }).first()).toBeVisible();
        await field.closeMenu();
      });
    }
  });

  test("As a user, I can insert a math equation into a document and read it back after saving", async ({
    pageWorkspace,
    pageDocument,
  }) => {
    const docName = uniqueName("e2e-doc-equation");
    const latex = "x^2 * \\sqrt{y}";

    await test.step("Given a document with an inserted equation", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);
      const field = await editor.getField("", 0);
      await field.insertEquation(latex);
      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then reopening it shows the same equation source", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      await expect(await pageDocument.equationInField("", 0)).toHaveAttribute("data-equation", latex);
    });
  });
});

dynamicUserTest.describe("Document CRUD", () => {
  dynamicUserTest(
    "As a user, toggling 'Show last modified date' reveals or hides a last-modified timestamp for each field",
    async ({ pageWorkspace, pageDocument }) => {
      const docName = uniqueName("e2e-doc-modification-date");

      await dynamicUserTest.step("Given an Experiment-form document with an edited field", async () => {
        await pageWorkspace.open();
        const doc = await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
        await doc.rename(docName);
        const field = await doc.editField("Method", 0);
        await field.fill(alphaNumericUnique("e2eModDateContent"));
        await field.save();
      });

      await dynamicUserTest.step("Then no last-modified date is shown by default", async () => {
        await pageWorkspace.searchFor(docName);
        await pageWorkspace.table.openRecord(docName);
        await pageDocument.isLoaded();

        await expect(pageDocument.lastModifiedDates.filter({ visible: true })).toHaveCount(0);
      });

      await dynamicUserTest.step("When I enable 'Show last modified date'", async () => {
        await pageDocument.header.showLastModifiedCheckbox.check();
      });

      await dynamicUserTest.step("Then a last-modified date appears for the document's fields", async () => {
        await expect(pageDocument.lastModifiedDates.first()).toBeVisible();
      });

      await dynamicUserTest.step("When I disable it again", async () => {
        await pageDocument.header.showLastModifiedCheckbox.uncheck();
      });

      await dynamicUserTest.step("Then the last-modified dates are hidden again", async () => {
        await expect(pageDocument.lastModifiedDates.filter({ visible: true })).toHaveCount(0);
      });
    },
  );

  dynamicUserTest(
    "As a user, documents and notebook entries open in edit or view mode as appropriate when created",
    async ({ pageWorkspace, pageDocument, pageNotebook }) => {
      await dynamicUserTest.step(
        "A basic document opens in edit mode when created, then view mode on reopen",
        async () => {
          await pageWorkspace.open();
          const editor = await pageWorkspace.createBasicDocument();
          await expect(pageDocument.editingStatus).toBeVisible();
          const docName = uniqueName("e2e-doc-opens-basic");
          await editor.header.rename(docName);
          await editor.editToolbar.saveAndClose();

          await pageWorkspace.searchFor(docName);
          await pageWorkspace.table.openRecord(docName);
          await pageDocument.isLoaded();
          await expect(pageDocument.editingStatus).toBeHidden();
        },
      );

      await dynamicUserTest.step(
        "An Experiment-form document opens in view mode when created via 'Choose a form'",
        async () => {
          await pageWorkspace.open();
          await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
          await expect(pageDocument.editingStatus).toBeHidden();
        },
      );

      await dynamicUserTest.step("A notebook entry opens in edit mode when created", async () => {
        await pageWorkspace.open();
        await pageWorkspace.createNotebook(uniqueName("e2e-doc-opens-nb"));
        const entry = await pageNotebook.addEntry();
        await expect(pageDocument.editingStatus).toBeVisible();
        await entry.editToolbar.saveAndClose();
      });
    },
  );

  dynamicUserTest(
    "As a user, the Sign button is hidden from a non-owner, and a witness can confirm a signed document",
    async ({ flowDocumentSession, appUser, pageWorkspace, pageDocument, clientSysadmin }) => {
      const groupName = uniqueName("e2e-doc-sign-group");
      const docName = uniqueName("e2e-doc-sign");

      const ownerUser = await dynamicUserTest.step(
        "Given a document owner exists in the same lab group as me (the PI)",
        async () => {
          const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDocSignOwner", "DocSignOwner");
          await clientSysadmin.createGroup({
            displayName: groupName,
            type: "LAB_GROUP",
            users: [
              { username: appUser.username, roleInGroup: "PI" },
              { username: user.username, roleInGroup: "DEFAULT" },
            ],
          });
          return user;
        },
      );

      const owner = await flowDocumentSession(ownerUser);

      await dynamicUserTest.step(
        "When the owner creates a document with content and shares it with the group",
        async () => {
          await owner.workspace.open();
          const editor = await owner.workspace.createBasicDocument();
          await editor.header.rename(docName);
          const field = await editor.getField("", 0);
          await field.fill(alphaNumericUnique("e2eDocSignContent"));
          await editor.editToolbar.saveAndClose();

          await owner.workspace.shareRecord(docName, { recipient: groupName, permission: "EDIT" });
        },
      );

      await dynamicUserTest.step("Then the Sign button is hidden from me, a non-owner group member", async () => {
        await pageWorkspace.searchFor(docName);
        await pageWorkspace.table.openRecord(docName);
        await pageDocument.isLoaded();
        expect(await pageDocument.canSign()).toBe(false);
      });

      await dynamicUserTest.step("When the owner signs it with me as witness", async () => {
        await owner.workspace.searchFor(docName);
        await owner.workspace.table.openRecord(docName);
        await owner.document.isLoaded();
        await owner.document.sign(ownerUser.password, [appUser.username]);
        await expect.poll(() => owner.document.isSigned()).toBe(true);
      });

      await dynamicUserTest.step("When I, as witness, confirm the signature", async () => {
        await pageWorkspace.searchFor(docName);
        await pageWorkspace.table.openRecord(docName);
        await pageDocument.isLoaded();
        expect(await pageDocument.canWitness()).toBe(true);
        await pageDocument.witness(appUser.password);
      });

      await dynamicUserTest.step("Then the document is fully witnessed, for both of us", async () => {
        await expect.poll(() => pageDocument.isWitnessed()).toBe(true);

        await owner.workspace.searchFor(docName);
        await owner.workspace.table.openRecord(docName);
        await owner.document.isLoaded();
        expect(await owner.document.isWitnessed()).toBe(true);
      });
    },
  );

  dynamicUserTest(
    "As a user, I can export a document to PDF from the document view",
    async ({ pageWorkspace, pageGallery, componentExportWizard }) => {
      const docName = uniqueName("e2e-doc-export");
      const content = alphaNumericUnique("e2eDocExportContent");

      const doc = await dynamicUserTest.step("Given an Experiment-form document with content exists", async () => {
        await pageWorkspace.open();
        const created = await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
        await created.rename(docName);
        const field = await created.editField("Method", 0);
        await field.fill(content);
        await field.saveAndFinishEditing();
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

      await dynamicUserTest.step(
        "Then a new PDF appears in Gallery > Exports, containing the document's content",
        async () => {
          const fileName = `${docName}.pdf`;
          await expect
            .poll(
              async () => {
                await pageGallery.openInSection("Exports");
                return pageGallery.fileCell(fileName).isVisible();
              },
              { timeout: 45_000 },
            )
            .toBe(true);
          const text = await pageGallery.downloadAndExtractText(fileName);
          expect(text).toContain(content);
        },
      );
    },
  );

  dynamicUserTest(
    "As a user, the Delete button is hidden from a document I don't own, even when it's shared with Edit permission",
    async ({ flowDocumentSession, appUser, pageWorkspace, clientSysadmin }) => {
      const docName = uniqueName("e2e-doc-delete-visibility");
      const groupName = uniqueName("e2e-doc-delete-visibility-group");

      const memberUser = await dynamicUserTest.step(
        "Given a group exists, and I share a document with it",
        async () => {
          const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDelVisMember", "DelVisMember");
          await clientSysadmin.createGroup({
            displayName: groupName,
            type: "LAB_GROUP",
            users: [
              { username: appUser.username, roleInGroup: "PI" },
              { username: user.username, roleInGroup: "DEFAULT" },
            ],
          });

          await pageWorkspace.open();
          const editor = await pageWorkspace.createBasicDocument();
          await editor.header.rename(docName);
          await editor.editToolbar.saveAndClose();

          await pageWorkspace.shareRecord(docName, { recipient: groupName, permission: "EDIT" });

          return user;
        },
      );

      await dynamicUserTest.step("Then the Delete button is hidden from the non-owner group member", async () => {
        const member = await flowDocumentSession(memberUser);
        await member.workspace.searchFor(docName);
        await member.workspace.table.openRecord(docName);
        await member.document.isLoaded();
        await expect(member.document.toolbar.actions.deleteButton).toBeHidden();
      });
    },
  );
});
