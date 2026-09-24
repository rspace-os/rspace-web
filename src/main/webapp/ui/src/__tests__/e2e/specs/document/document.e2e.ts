import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

const MULTI_FIELD_FORM = "Experiment";

/** Deletes the named record and verifies it's gone from the listing and no longer searchable by content. */
async function deleteAndVerifyGone(
  pageWorkspace: WorkspacePage,
  docName: string,
  content: string,
  deleteDocument: { description: string; run: () => Promise<void> },
): Promise<void> {
  await test.step("Given searching its content finds exactly this document", async () => {
    await pageWorkspace.open();
    await expect(async () => {
      await pageWorkspace.searchBar.search(content);
      expect(await pageWorkspace.table.rowCount()).toBe(1);
      await expect(pageWorkspace.table.row(docName)).toBeVisible({ timeout: 1_000 });
    }).toPass({ timeout: 30_000 });
  });

  await test.step(`When I delete it ${deleteDocument.description}`, deleteDocument.run);

  await test.step("Then it's no longer found by name", async () => {
    await pageWorkspace.searchFor(docName);
    await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
  });

  await test.step("And it's no longer searchable by its content", async () => {
    await pageWorkspace.searchBar.search(content);
    expect(await pageWorkspace.table.rowCount()).toBe(0);
  });
}

test.describe("Document CRUD and editing", () => {
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

    await deleteAndVerifyGone(pageWorkspace, docName, content, {
      description: "from the workspace selection bar",
      run: async () => {
        await pageWorkspace.open();
        await pageWorkspace.table.selectRecord(docName);
        await pageWorkspace.selectionBar.delete();
      },
    });
  });

  test("As a user, I can delete an experiment document and it's no longer searchable by its content", async ({
    pageWorkspace,
  }) => {
    const docName = uniqueName("e2e-doc-delete-experiment");
    const content = alphaNumericUnique("e2edocdeleteexperimentcontent");

    const docId = await test.step("Given an Experiment-form document with content exists", async () => {
      await pageWorkspace.open();
      const doc = await pageWorkspace.createDocumentFromForm(MULTI_FIELD_FORM);
      await doc.rename(docName);

      const field = await doc.editField("Method", 0);
      await field.fill(content);
      await field.saveAndFinishEditing();
      return doc.getId();
    });

    await deleteAndVerifyGone(pageWorkspace, docName, content, {
      description: "from the document view's Delete button",
      run: async () => {
        const doc = await pageWorkspace.openDocument(docId);
        await doc.delete();
      },
    });
  });

  test("As a user, Save & Clone, Save & New, and Save & View each save my edit before leaving it", async ({
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-doc-save-variants");
    const content1 = uniqueName("e2e-doc-save-variants-content1");
    const content2 = uniqueName("e2e-doc-save-variants-content2");
    const content3 = uniqueName("e2e-doc-save-variants-content3");
    const content4 = uniqueName("e2e-doc-save-variants-content4");

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

    const originalId = pageDocument.getId();

    await test.step("When I edit it and use Save & New, the edit is saved and I land on a different, fresh document", async () => {
      const field = await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      await field.fill(content3);
      await pageDocumentEditor.editToolbar.saveAndNew();
      await pageDocument.isLoaded();

      expect(pageDocument.getId()).not.toBe(originalId);
      const freshContent = await pageDocument.getFieldViewContent("", 0);
      expect(await freshContent.innerText()).not.toContain(content3);
      expect((await clientDocuments.getById(originalId)).fields[0].content).toContain(content3);
    });

    await test.step("When I edit it again and use Save & View, the view shows the saved edit", async () => {
      await pageWorkspace.searchFor(docName);
      await pageWorkspace.table.openRecord(docName);
      const field = await pageDocument.editField("", 0);
      await pageDocumentEditor.isLoaded();
      await field.fill(content4);
      const viewed = await pageDocumentEditor.saveAndView();

      expect(viewed.getId()).toBe(originalId);
      const content = await viewed.getFieldViewContent("", 0);
      expect(await content.innerText()).toBe(content4);
      expect((await clientDocuments.getById(originalId)).fields[0].content).toContain(content4);
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

dynamicUserTest.describe("Document view modes, signing, permissions, and exports", () => {
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
        await field.saveAndFinishEditing();
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
    "As a user, the Sign button is hidden from a non-owner, a wrong password is rejected, the signed content is checksum-verified and rename-locked, and a witness can decline or confirm",
    async ({ flowDocumentSession, appUser, pageWorkspace, pageDocument, clientSysadmin }) => {
      dynamicUserTest.setTimeout(120_000);
      const groupName = uniqueName("e2e-doc-sign-group");
      const docName = uniqueName("e2e-doc-sign");
      const declineReason = alphaNumericUnique("e2eDocSignDeclineReason");

      const { ownerUser, decliningWitnessUser } = await dynamicUserTest.step(
        "Given a document owner and a second witness exist in the same lab group as me (the PI)",
        async () => {
          const owner = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDocSignOwner", "DocSignOwner");
          const decliningWitness = await createDynamicUser(
            clientSysadmin,
            "ROLE_USER",
            "e2eDocSignDecline",
            "DocSignDecline",
          );
          await clientSysadmin.createGroup({
            displayName: groupName,
            type: "LAB_GROUP",
            users: [
              { username: appUser.username, roleInGroup: "PI" },
              { username: owner.username, roleInGroup: "DEFAULT" },
              { username: decliningWitness.username, roleInGroup: "DEFAULT" },
            ],
          });
          return { ownerUser: owner, decliningWitnessUser: decliningWitness };
        },
      );

      const owner = await flowDocumentSession(ownerUser);
      const decliningWitnessSession = await flowDocumentSession(decliningWitnessUser);

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
        await expect(pageDocument.toolbar.signButton).toBeHidden();
      });

      await dynamicUserTest.step(
        "When the owner attempts to sign with the wrong password, then retries with the correct one",
        async () => {
          await owner.workspace.searchFor(docName);
          await owner.workspace.table.openRecord(docName);
          await owner.document.isLoaded();
          const { dialog, alert } = await owner.document.signExpectingInvalidPassword("not-the-password", [
            appUser.username,
            decliningWitnessUser.username,
          ]);
          await expect(alert.message).toContainText("Invalid password");
          await alert.confirm();
          await expect(owner.document.signedStatus).toHaveCount(0);

          await dialog.retryWithPassword(ownerUser.password);
          await expect(owner.document.signedStatus.first()).toBeVisible();
        },
      );

      await dynamicUserTest.step(
        "Then the signature status, content checksum, signed icon, and rename lock all reflect the signature",
        async () => {
          await expect(owner.document.signedAwaitingWitnessStatus).toBeVisible();
          const statusMessage = await owner.document.openSignatureStatusMessage();
          await expect(statusMessage).toContainText(`This document was signed by ${ownerUser.fullName}`);

          const checksumLabel = await owner.document.showSignatureChecksum();
          await expect(checksumLabel).toContainText("SHA-256 checksum of the signed content");
          await expect(await owner.document.verifySignatureChecksum()).toBeVisible();

          await owner.workspace.searchFor(docName);
          await expect(owner.workspace.table.signedIcon(docName)).toBeVisible();
          await owner.workspace.table.selectRecord(docName);
          const renameDialog = await owner.workspace.selectionBar.renameExpectingRejection(`${docName}-renamed`);
          const renameError = owner.toasts.byVariant("error", "");
          await owner.toasts.expandSubMessages(renameError);
          await expect(renameError).toContainText(
            "The document could not be renamed. It may be signed or locked for editing.",
          );
          await renameDialog.cancel();
          await owner.workspace.searchFor(docName);
          await expect(owner.workspace.table.row(docName)).toBeVisible();
        },
      );

      await dynamicUserTest.step(
        "When the second witness opens the signing request message and declines with a reason, they can no longer witness it",
        async () => {
          await decliningWitnessSession.workspace.open();
          const messages = await decliningWitnessSession.workspace.openReceivedMessages();
          await expect(messages.messagesWithSubject("Witness document signing request")).toHaveCount(1);
          await messages.openLinkedRecord(docName);
          await decliningWitnessSession.document.isLoaded();
          await decliningWitnessSession.document.declineWitness(decliningWitnessUser.password, declineReason);
          await expect(decliningWitnessSession.document.signedAwaitingWitnessStatus).toBeVisible();
          await expect(decliningWitnessSession.document.toolbar.witnessButton).toBeHidden();
        },
      );

      await dynamicUserTest.step(
        "Then the owner sees the decline and a notification naming the declining witness and reason",
        async () => {
          await owner.workspace.searchFor(docName);
          await owner.workspace.table.openRecord(docName);
          await owner.document.isLoaded();
          await expect(
            owner.document.statusText(`Declined witness requests: ${decliningWitnessUser.fullName}.`),
          ).toBeVisible();

          await owner.notifications.open();
          await expect(owner.notifications.row("has declined to witness your signature", declineReason)).toBeVisible();
        },
      );

      await dynamicUserTest.step(
        "When I, the remaining witness, open the request and confirm the signature",
        async () => {
          await pageWorkspace.open();
          await pageWorkspace.openMessageLinkedDocument(docName);
          await expect(await pageDocument.showSignatureChecksum()).toContainText(
            "SHA-256 checksum of the signed content",
          );
          await expect(await pageDocument.verifySignatureChecksum()).toBeVisible();
          await expect(pageDocument.toolbar.witnessButton).toBeVisible();
          await pageDocument.witness(appUser.password);
        },
      );

      await dynamicUserTest.step("Then the document is fully witnessed, for both the owner and me", async () => {
        await expect(pageDocument.witnessedStatus).toBeVisible();

        await owner.workspace.searchFor(docName);
        await owner.workspace.table.openRecord(docName);
        await owner.document.isLoaded();
        await expect(owner.document.witnessedStatus).toBeVisible();
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
    "As a user, the Delete button is hidden from a document I don't own, even as the group's PI with Edit permission",
    async ({ flowDocumentSession, appUser, pageWorkspace, pageDocument, clientSysadmin }) => {
      const docName = uniqueName("e2e-doc-delete-visibility");
      const groupName = uniqueName("e2e-doc-delete-visibility-group");

      const { owner, otherMemberUser } = await dynamicUserTest.step(
        "Given a plain member owns a document shared with our lab group at Edit permission",
        async () => {
          const ownerUser = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDelVisOwner", "DelVisOwner");
          const other = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDelVisMember", "DelVisMember");
          await clientSysadmin.createGroup({
            displayName: groupName,
            type: "LAB_GROUP",
            users: [
              { username: appUser.username, roleInGroup: "PI" },
              { username: ownerUser.username, roleInGroup: "DEFAULT" },
              { username: other.username, roleInGroup: "DEFAULT" },
            ],
          });
          const ownerSession = await flowDocumentSession(ownerUser);
          await ownerSession.documents.create({ name: docName, fields: [{ content: "Owned by a plain member" }] });
          await ownerSession.workspace.shareRecord(docName, { recipient: groupName, permission: "EDIT" });
          return { owner: ownerSession, otherMemberUser: other };
        },
      );

      await dynamicUserTest.step("Then the owner sees the Delete button", async () => {
        await owner.workspace.searchFor(docName);
        await owner.workspace.table.openRecord(docName);
        await owner.document.isLoaded();
        await expect(owner.document.toolbar.actions.deleteButton).toBeVisible();
      });

      await dynamicUserTest.step("And it's hidden from me, the group's PI, who doesn't own it", async () => {
        await pageWorkspace.searchFor(docName);
        await pageWorkspace.table.openRecord(docName);
        await pageDocument.isLoaded();
        await expect(pageDocument.toolbar.actions.deleteButton).toBeHidden();
      });

      await dynamicUserTest.step("And it's hidden from another non-owner group member", async () => {
        const member = await flowDocumentSession(otherMemberUser);
        await member.workspace.searchFor(docName);
        await member.workspace.table.openRecord(docName);
        await member.document.isLoaded();
        await expect(member.document.toolbar.actions.deleteButton).toBeHidden();
      });
    },
  );
});
