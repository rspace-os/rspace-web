import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

function countCsvDataRows(csvContent: string): number {
  const lines = csvContent.split("\n").filter((line) => line.trim().length > 0);
  return lines.length - 1;
}

test.describe("Sysadmin access and role", { tag: tags.SYSTEM }, () => {
  test("A sysadmin sees a user's Last Login update in the Users grid after they log in", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSysAccessLogin");

    const { users } = flowSysadminGroupAdmin;
    await users.open();
    await users.search(user.username);
    const lastLoginBeforeLogin = await users.lastLoginFor(user.username);

    // A fresh browser context login is itself the observable event under test.
    await flowUserSession(user.username, DYNAMIC_USER_PASSWORD);

    await users.open();
    await users.search(user.username);
    const lastLoginAfterLogin = await users.lastLoginFor(user.username);

    expect(lastLoginAfterLogin).not.toBe(lastLoginBeforeLogin);
  });

  test("A sysadmin can create and delete another System Admin account", async ({ flowSysadminGroupAdmin }) => {
    const { createAccount, users } = flowSysadminGroupAdmin;

    await users.open();
    const totalUsersBefore = await users.totalUsers();
    const sysadminsBefore = await users.systemAdmins();

    const username = uniqueName("e2eNewSysadmin");
    await createAccount.open();
    await createAccount.selectTab("System Admin");
    await createAccount.fillBasicFields({
      firstName: "E2E",
      lastName: "NewSysadmin",
      username,
      email: `${username}@example.com`,
      password: DYNAMIC_USER_PASSWORD,
    });
    await createAccount.submitExpectingSuccess();

    await users.open();
    await users.search(username);
    await expect(users.userRow(username)).toContainText("Sysadmin");
    expect(await users.totalUsers()).toBe(totalUsersBefore + 1);
    expect(await users.systemAdmins()).toBe(sysadminsBefore + 1);

    await users.deleteUser(username);

    await users.open();
    expect(await users.totalUsers()).toBe(totalUsersBefore);
    expect(await users.systemAdmins()).toBe(sysadminsBefore);
  });

  test("A sysadmin has read-only access to any user's document", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eSysReadAllDocs");
    const userSession = await flowUserSession(user.username, DYNAMIC_USER_PASSWORD);
    const editor = await userSession.workspace.createBasicDocument();
    const docId = editor.getId();

    const doc = await flowSysadminGroupAdmin.workspace.openDocument(docId);
    await expect(doc.readOnlyStatus).toBeVisible();
    // createBasicDocument()'s known default title, established elsewhere in this migration.
    await expect(doc.header.name).toHaveText("Untitled document");
  });

  test("A Form created by a user is transferred to sysadmin when another user has used it, and the owner is deleted", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    test.skip(true, "Known issue: Field Editor dialog never closes after adding a Number field: PRT-1148");

    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eFormXferMember");
    const formName = uniqueName("e2eFormXferForm");

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    await memberSession.myRSpace.open();
    const createFormPage = await memberSession.myRSpace.navigateToCreateFormPage();
    await createFormPage.addNumberField("Value");
    await createFormPage.rename(formName);
    const memberManageForms = await createFormPage.saveAndClose();
    await memberManageForms.publishWithPermissions(formName, "NONE", "NONE");

    const { users, auditTrail, workspace: sysWorkspace, myRSpace: sysMyRSpace } = flowSysadminGroupAdmin;
    await sysMyRSpace.open();
    const sysManageForms = await sysMyRSpace.navigateToManageFormsPage();

    await sysManageForms.showAllForms();
    await sysManageForms.search(formName);
    await sysManageForms.addToMenu(formName);

    await sysWorkspace.open();
    await sysWorkspace.createDocumentFromCustomForm(formName);

    await users.open();
    await users.deleteUser(member.username);
    await users.searchExpectingNoResults(member.username);

    await auditTrail.open();
    await auditTrail.isLoaded();
    await auditTrail.checkAction("TRANSFER");
    await auditTrail.submitQuery();
    const transferRow = auditTrail.rowsWithName(formName);
    await expect(transferRow).toHaveCount(1);
    await expect(transferRow).toContainText(member.username);

    await sysMyRSpace.open();
    const sysManageFormsAfter = await sysMyRSpace.navigateToManageFormsPage();
    await expect(sysManageFormsAfter.formRow(formName)).toBeVisible();
  });

  test("A sysadmin can export the Users grid to CSV", async ({ clientSysadmin, flowSysadminGroupAdmin }) => {
    const user = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eExportCsv");
    const { users } = flowSysadminGroupAdmin;
    const tempDir = mkdtempSync(join(tmpdir(), "rspace-e2e-export-csv-"));

    await users.open();
    await users.selectUser(user.username);
    const selectedDownload = await users.exportSelectedRowsToCsv();
    const selectedPath = join(tempDir, "selected.csv");
    await selectedDownload.saveAs(selectedPath);
    expect(countCsvDataRows(readFileSync(selectedPath, "utf-8"))).toBe(1);

    await users.open();
    const pageRowCount = await users.rowCount();
    const pageDownload = await users.exportThisPageToCsv();
    const pagePath = join(tempDir, "page.csv");
    await pageDownload.saveAs(pagePath);
    expect(countCsvDataRows(readFileSync(pagePath, "utf-8"))).toBe(pageRowCount);
  });
});
