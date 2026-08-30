import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { fixturePath } from "@/__tests__/e2e/testData";

const EVERNOTE_DUMP_ENEX = fixturePath(import.meta.url, "fixtures/EvernoteDump.enex");

const NOTE_TITLES = ["note1", "Untitled", "Eisenhower Matrix", "Meeting Notes", "Menu Planner"];

test.describe("Evernote integration [mock]", { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("evernote.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Evernote", true);
  });

  test("As a user, I can import an Evernote .enex export as a folder of documents", async ({ pageWorkspace }) => {
    await pageWorkspace.open();

    const dialog = await pageWorkspace.openEvernoteImportDialog();
    await dialog.importFile(EVERNOTE_DUMP_ENEX);

    await expect(pageWorkspace.table.row("EvernoteDump")).toBeVisible({ timeout: 15_000 });
    await pageWorkspace.table.openRecord("EvernoteDump");
    await pageWorkspace.waitUntilBreadcrumbShows("EvernoteDump");

    for (const title of NOTE_TITLES) {
      await expect(pageWorkspace.table.row(title)).toBeVisible();
    }
  });
});
