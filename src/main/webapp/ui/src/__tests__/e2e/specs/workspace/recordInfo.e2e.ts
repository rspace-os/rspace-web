import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { NotebookPage } from "@/__tests__/e2e/pageObjects/notebook/NotebookPage";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";

const CREATED_USER_PASSWORD = "Passw0rd!23";

function expectGlobalId(id: string): void {
  expect(id.startsWith("SD")).toBe(true);
  const suffix = id.slice(2);
  expect(suffix.length > 0 && [...suffix].every((c) => c >= "0" && c <= "9")).toBe(true);
}

test.describe("Record Info", () => {
  test("As a user, signing a document is reflected in its Record Info", async ({ pageWorkspace, appUser }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    // An empty document has no Sign button — it needs real content before it can be signed.
    const field = await editor.getField("New List of Materials");
    await field.fill("some content to sign");
    const doc = await editor.saveAndView();

    await test.step("Then Record Info initially shows it as unsigned", async () => {
      const info = await doc.openRecordInfo();
      await expect.poll(() => info.field("Signature Status")).toBe("unsigned");
      await info.close();
    });

    await test.step("When I sign the document", async () => {
      await doc.signWithoutWitness(appUser.password);
    });

    await test.step("Then Record Info shows it as signed", async () => {
      const info = await doc.openRecordInfo();
      await expect.poll(() => info.field("Signature Status")).toBe("signed");
      await info.close();
    });
  });

  test("As a user, sharing a document with a group is reflected in its Record Info", async ({
    pageWorkspace,
    clientSysadmin,
    appUser,
  }) => {
    const groupName = uniqueName("e2e-record-info-group");
    const memberUsername = alphaNumericUnique("e2eRecordInfoMember");
    const docName = "Untitled document";

    await test.step("Given a lab group I'm the PI of", async () => {
      await clientSysadmin.createUser({
        username: memberUsername,
        password: CREATED_USER_PASSWORD,
        email: `${memberUsername}@example.com`,
        firstName: "E2E",
        lastName: "RecordInfoMember",
        role: "ROLE_USER",
      });
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: memberUsername, roleInGroup: "DEFAULT" },
        ],
      });
    });

    await test.step("And a saved document", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      await editor.saveAndView();
      await pageWorkspace.open();
    });

    await test.step("Then Record Info initially shows it as not shared", async () => {
      const info = await pageWorkspace.openInfoFor(docName);
      expect(await info.isShared()).toBe(false);
      await info.close();
    });

    await test.step("When I share it with the group", async () => {
      await pageWorkspace.table.selectRecord(docName);
      const shareDialog = await pageWorkspace.selectionBar.share();
      await shareDialog.addRecipient(groupName);
      await shareDialog.save();
    });

    await test.step("Then Record Info shows it as shared", async () => {
      const info = await pageWorkspace.openInfoFor(docName);
      expect(await info.isShared()).toBe(true);
      await info.close();
    });
  });

  test("As a user, each notebook entry has its own distinct Record Info", async ({ pageWorkspace }) => {
    const notebookName = uniqueName("e2e-record-info-notebook");
    let notebook: NotebookPage;
    const entryGlobalIds: string[] = [];

    await test.step("Given a notebook with two entries", async () => {
      await pageWorkspace.open();
      notebook = await pageWorkspace.createNotebook(notebookName);
      for (const _ of [...Array(2).keys()]) {
        const entry = await notebook.addEntry();
        const info = await entry.openRecordInfo();
        entryGlobalIds.push(await info.field("Unique Id"));
        await info.close();
        await entry.editToolbar.saveAndClose();
      }
    });

    await test.step("Then the two entries have distinct, correctly-formed Global IDs", () => {
      expectGlobalId(entryGlobalIds[0]);
      expectGlobalId(entryGlobalIds[1]);
      expect(entryGlobalIds[0]).not.toBe(entryGlobalIds[1]);
    });
  });
});
