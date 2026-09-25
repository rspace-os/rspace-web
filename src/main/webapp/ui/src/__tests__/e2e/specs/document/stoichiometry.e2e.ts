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

      await test.step("When I insert a reaction table via the /stoichiometry slash command", async () => {
        const field = await editor.getField("", 0);
        const dialog = await field.insertStoichiometryTableViaSlashCommand();
        await dialog.close();
      });

      await test.step("Then a blank reaction table placeholder replaces the typed command in the field", async () => {
        const field = await editor.getField("", 0);
        await expect(field.stoichiometryTablePlaceholder).toBeVisible();
        await expect(field.body).not.toContainText("/stoichiometry");
      });

      await editor.editToolbar.saveAndClose();
    });

    await test.step("Then the blank reaction table survives save and reload", async () => {
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.openRecord(docName);
      await pageDocument.isLoaded();
      const table = await pageDocument.getStoichiometryTable("", 0);
      await expect(table.grid.getByText("No rows", { exact: true })).toBeVisible();
      await expect(table.dataRows()).toHaveCount(0);
    });
  });

  test("As a user, an edited reagent mass and its recalculated moles persist in the saved document view", async ({
    pageWorkspace,
    pageDocument,
  }) => {
    const docName = uniqueName("e2e-stoich-mass");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(docName);
    const field = await editor.getField("", 0);
    const reaction = await field.insertStoichiometryTable();
    await reaction.addManually("Ethanol", "CCO");
    await reaction.editCell("Ethanol", "mass", "4.607");
    await expect.poll(async () => Number(await reaction.getCellText("Ethanol", "moles"))).toBeCloseTo(0.1, 4);
    await reaction.saveChanges();
    await reaction.close();
    await editor.editToolbar.saveAndClose();

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
    flowRefreshDocumentSession,
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
    await flowRefreshDocumentSession();

    const docName = uniqueName("e2e-stoich-memberships");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(docName);
    const field = await editor.getField("", 0);
    const reaction = await field.insertStoichiometryTable();
    await reaction.addManually("Ethanol", "CCO");
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
    await sharedReaction.addManually("Methanol", "CO");
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
