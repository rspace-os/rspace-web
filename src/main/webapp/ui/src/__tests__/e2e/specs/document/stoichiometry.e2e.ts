import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Stoichiometry reaction table", () => {
  test.beforeEach(async ({ flowSysadminConfig, pageApps }) => {
    await flowSysadminConfig.ensureSetting("chemistry.available", "ALLOWED");
    await pageApps.setEnabled("Chemistry", true);
  });

  test("As a user, a blank reaction table opens directly on insertion and survives save and reload", async ({
    pageWorkspace,
    pageDocument,
  }) => {
    const docName = uniqueName("e2e-stoich-blank");

    await test.step("Given a basic document exists", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.header.rename(docName);

      await test.step("When I insert a reaction table via the toolbar", async () => {
        const field = await editor.getField("", 0);
        const dialog = await field.insertStoichiometryTable();
        await dialog.close();
      });

      await test.step("Then a blank reaction table placeholder is present in the field", async () => {
        const field = await editor.getField("", 0);
        expect(await field.hasBlankStoichiometryTable()).toBe(true);
      });

      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then the blank reaction table survives save and reload", async () => {
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const table = await pageDocument.getStoichiometryTable("", 0);
      await expect(table.grid).toBeVisible();
      expect(await table.getCompoundCount()).toBe(0);
    });
  });

  test("As a user, a reagent retains its molecular weight and mass calculations after reopening", async ({
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
  }) => {
    const docName = uniqueName("e2e-stoich-weight");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(docName);
    const field = await editor.getField("", 0);
    const reaction = await field.insertStoichiometryTable();
    await reaction.addPubChemCompound("Ethanol");
    await expect
      .poll(async () => Number(await reaction.getCellText("Ethanol", "Molecular Weight (g/mol)")))
      .toBeCloseTo(46.07, 2);
    await reaction.saveChanges();
    await reaction.close();
    await editor.editToolbar.saveAndClose();

    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.isLoaded();
    const reopenedField = await pageDocument.editField("", 0);
    const reopened = await reopenedField.viewStoichiometryTable();
    await expect(reopened.dataRows()).toHaveCount(1);
    await expect
      .poll(async () => Number(await reopened.getCellText("Ethanol", "Molecular Weight (g/mol)")))
      .toBeCloseTo(46.07, 2);
    await reopened.editCell("Ethanol", "Mass (g)", "4.607");
    await expect.poll(async () => Number(await reopened.getCellText("Ethanol", "Moles (mol)"))).toBeCloseTo(0.1, 4);
    await reopened.saveChanges();
    await reopened.close();
    await pageDocumentEditor.editToolbar.saveAndClose();

    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.isLoaded();
    const saved = await pageDocument.getStoichiometryTable("", 0);
    await expect(saved.dataRows()).toHaveCount(1);
    await expect.poll(async () => Number(await saved.getCellText("Ethanol", "Mass (g)"))).toBeCloseTo(4.607, 3);
    await expect.poll(async () => Number(await saved.getCellText("Ethanol", "Moles (mol)"))).toBeCloseTo(0.1, 4);
  });

  test("As a user, sharing a reaction with two lab groups never duplicates its compounds", async ({
    appUser,
    clientSysadmin,
    pageLogin,
    pageWorkspace,
    pageDocument,
    pageDocumentEditor,
  }) => {
    test.setTimeout(90_000);
    const groupNames = [uniqueName("e2e-reaction-group-a"), uniqueName("e2e-reaction-group-b")];
    for (const displayName of groupNames) {
      await clientSysadmin.createGroup({
        displayName,
        type: "LAB_GROUP",
        users: [{ username: appUser.username, roleInGroup: "PI" }],
      });
    }
    // Remove after the Shiro membership-cache bug is fixed; see the migration bug note.
    await pageWorkspace.open();
    await pageWorkspace.header.logOut();
    await pageLogin.open();
    await pageLogin.login(appUser.username, appUser.password);

    const docName = uniqueName("e2e-stoich-memberships");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(docName);
    const field = await editor.getField("", 0);
    const reaction = await field.insertStoichiometryTable();
    await reaction.addSmilesManually("Ethanol", "CCO");
    await reaction.saveChanges();
    await reaction.close();
    await editor.editToolbar.saveAndClose();

    for (const groupName of groupNames) {
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const saved = await pageDocument.getStoichiometryTable("", 0);
      await expect(saved.dataRows()).toHaveCount(1);
      await expect(saved.rowByCompoundName("Ethanol")).toBeVisible();
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
      const share = await pageWorkspace.selectionBar.share();
      await share.addRecipient(groupName);
      await share.setPermission(groupName, "EDIT");
      await share.save();
    }

    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.isLoaded();
    const shared = await pageDocument.editField("", 0);
    const sharedReaction = await shared.viewStoichiometryTable();
    await expect(sharedReaction.dataRows()).toHaveCount(1);
    await sharedReaction.addSmilesManually("Methanol", "CO");
    await sharedReaction.saveChanges();
    await sharedReaction.close();
    await pageDocumentEditor.editToolbar.saveAndClose();

    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.isLoaded();
    const saved = await pageDocument.getStoichiometryTable("", 0);
    await expect(saved.dataRows()).toHaveCount(2);
    await expect(saved.rowByCompoundName("Ethanol")).toBeVisible();
    await expect(saved.rowByCompoundName("Methanol")).toBeVisible();
  });
});
