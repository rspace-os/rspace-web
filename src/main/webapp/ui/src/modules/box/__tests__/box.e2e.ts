import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { BoxPickerFlow } from "./pageObjects/BoxPickerFlow";

test.describe("Box integration [real]", { tag: tags.APPS }, () => {
  test.skip(
    !(env.boxUsername && env.boxPassword),
    "real mode needs BOX_USERNAME/BOX_PASSWD (a real Box account) in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("box.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledForBox("Box");
  });

  test("As a user, I can insert a file from Box into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const popup = await docEditor.openBoxPicker();

    const flow = new BoxPickerFlow(popup);
    await flow.login(env.boxUsername, env.boxPassword);
    const fileName = await flow.selectFirstFileAndChoose();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(fileName);
  });
});
