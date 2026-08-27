import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

test.describe("OneDrive integration [real]", { tag: tags.APPS }, () => {
  test.skip(
    !(env.onedriveUsername && env.onedrivePassword),
    "real mode needs ONEDRIVE_USERNAME/ONEDRIVE_PASSWORD (a real Microsoft/OneDrive account) in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("onedrive.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabled("OneDrive", true);
  });

  test("As a user, I can insert a file from OneDrive into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const popup = await docEditor.openOneDrivePicker();
    await popup.waitForLoadState();
  });
});
