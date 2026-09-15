import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

test.describe("Dropbox integration [real]", { tag: tags.APPS }, () => {
  test.skip(
    !(env.dropboxUsername && env.dropboxPassword),
    "real mode needs DROPBOX_USERNAME/DROPBOX_PASSWD (a real, email-verified Dropbox account) in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dropbox.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("Dropbox", true);
  });

  test("As a user, I can insert a file from Dropbox into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const popup = await docEditor.openDropboxPicker();
    await popup.waitForLoadState();
  });
});
