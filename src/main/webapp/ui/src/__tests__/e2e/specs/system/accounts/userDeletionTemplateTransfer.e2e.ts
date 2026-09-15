import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

const currentDir = dirname(fileURLToPath(import.meta.url));
const TINY_IMAGE_PATH = resolve(currentDir, "fixtures/tinyImage.png");
const TINY_IMAGE_NAME = "tinyImage.png";

test.describe("User deletion and template transfer", { tag: tags.SYSTEM }, () => {
  test("A template shared but only used by its own owner is not transferred when the owner is deleted", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eDelPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eDelMember");
    const groupName = uniqueName("e2eDelGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    const editor = await memberSession.workspace.createBasicDocument();
    const doc = await editor.saveAndView();
    await doc.reload();
    const templateName = uniqueName("e2eDelTemplate");
    await doc.saveAsTemplate(templateName);

    await memberSession.workspace.open();
    await memberSession.workspace.toolbar.toggleFilter("templates");
    await memberSession.workspace.table.selectRecord(templateName);
    const shareDialog = await memberSession.workspace.selectionBar.share();
    await shareDialog.addRecipient(groupName);
    await shareDialog.save();

    await memberSession.workspace.createDocumentFromTemplate(templateName, uniqueName("e2eDelDoc"));

    const { users, auditTrail } = flowSysadminGroupAdmin;
    await users.open();
    await users.deleteUser(member.username);
    await users.searchExpectingNoResults(member.username);

    await auditTrail.open();
    await auditTrail.isLoaded();
    await auditTrail.checkAction("TRANSFER");
    await auditTrail.submitQuery();
    await expect(auditTrail.rowsWithName(templateName)).toHaveCount(0);
  });

  test("A template shared with a group and actually used by another member is transferred to sysadmin when the owner is deleted", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    // Heavier than a typical spec: three concurrently-open real browser sessions (member, PI,
    // sysadmin), each doing several full navigations.
    test.setTimeout(120_000);
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eXferPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eXferMember");
    const groupName = uniqueName("e2eXferGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    const editor = await memberSession.workspace.createBasicDocument();
    const doc = await editor.saveAndView();
    await doc.reload();
    const templateName = uniqueName("e2eXferTemplate");
    await doc.saveAsTemplate(templateName);

    await memberSession.workspace.open();
    await memberSession.workspace.toolbar.toggleFilter("templates");
    await memberSession.workspace.table.selectRecord(templateName);
    const shareDialog = await memberSession.workspace.selectionBar.share();
    await shareDialog.addRecipient(groupName);
    await shareDialog.save();

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.workspace.open();
    const piDocName = uniqueName("e2eXferPiDoc");
    const piDoc = await piSession.workspace.createDocumentFromSharedTemplate(templateName, piDocName);
    const piDocId = piDoc.getId();
    await piDoc.saveAndView();

    const { users, auditTrail, workspace: sysWorkspace } = flowSysadminGroupAdmin;
    await users.open();
    await users.deleteUser(member.username);
    await users.searchExpectingNoResults(member.username);

    await auditTrail.open();
    await auditTrail.isLoaded();
    await auditTrail.checkAction("TRANSFER");
    await auditTrail.submitQuery();
    const transferRow = auditTrail.rowsWithName(templateName);
    await expect(transferRow).toHaveCount(1);
    await expect(transferRow).toContainText(member.username);

    await sysWorkspace.open();
    await sysWorkspace.searchBar.search(templateName);
    await expect(sysWorkspace.table.row(templateName)).toBeVisible();

    const piDocAfterDeletion = await piSession.workspace.openDocument(piDocId);
    await expect(piDocAfterDeletion.header.name).toHaveText(piDocName);
  });

  test("A gallery image embedded in a template that another member actually used is transferred to sysadmin's 'Deleted Users' folder when the owner is deleted", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    // Heaviest test in this file: gallery upload + TinyMCE
    test.setTimeout(150_000);
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eGalXferPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eGalXferMember");
    const groupName = uniqueName("e2eGalXferGroup");
    await clientSysadmin.createGroup({
      displayName: groupName,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    const editor = await memberSession.workspace.createBasicDocument();
    const picker = await editor.openGalleryPicker();
    await picker.goToSection("Images");
    await picker.uploadFile(TINY_IMAGE_PATH, TINY_IMAGE_NAME);
    await picker.selectItem(TINY_IMAGE_NAME);
    await picker.add();
    const doc = await editor.saveAndView();
    await doc.reload();
    const templateName = uniqueName("e2eGalXferTemplate");
    await doc.saveAsTemplate(templateName);

    await memberSession.workspace.open();
    await memberSession.workspace.toolbar.toggleFilter("templates");
    await memberSession.workspace.table.selectRecord(templateName);
    const shareDialog = await memberSession.workspace.selectionBar.share();
    await shareDialog.addRecipient(groupName);
    await shareDialog.save();

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.workspace.open();
    await piSession.workspace.createDocumentFromSharedTemplate(templateName, uniqueName("e2eGalXferPiDoc"));

    const { users, auditTrail, workspace: sysWorkspace, gallery } = flowSysadminGroupAdmin;
    await users.open();
    await users.deleteUser(member.username);
    await users.searchExpectingNoResults(member.username);

    await auditTrail.open();
    await auditTrail.isLoaded();
    await auditTrail.checkAction("TRANSFER");
    await auditTrail.submitQuery();
    await expect(auditTrail.rowsWithName(templateName)).toHaveCount(1);

    await sysWorkspace.open();
    await sysWorkspace.searchBar.search(templateName);
    await expect(sysWorkspace.table.row(templateName)).toBeVisible();

    await gallery.open();
    await gallery.isLoaded();
    await gallery.openSection("Images");
    await gallery.openFolder("Deleted Users");
    await gallery.openFolder(member.username);
    await gallery.waitForFile(TINY_IMAGE_NAME);
  });
});
