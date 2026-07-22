import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const currentDir = dirname(fileURLToPath(import.meta.url));
const ATTACHMENT_PATH = resolve(currentDir, "fixtures/galaxy-attachment.txt");
const ATTACHMENT_NAME = "galaxy-attachment.txt";

const ATTACHMENT_LINK_NAME = "galaxy-attachment";

const INTEGRATION_MODE = env.integrationMode;
const GALAXY_ALIAS = INTEGRATION_MODE === "real" ? "galaxy eu server" : "mock";
const GALAXY_API_KEY = INTEGRATION_MODE === "real" ? env.galaxyEuApiKey : "mock-galaxy-token";

test.describe(`Galaxy integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !GALAXY_API_KEY,
    "real mode needs GALAXY_EU_APIKEY (a usegalaxy.eu API key) in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("galaxy.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("Galaxy", GALAXY_API_KEY, {
      textboxName: `API Key for ${GALAXY_ALIAS}`,
      serverAlias: GALAXY_ALIAS,
    });
  });

  test("As a user, I can upload an attached file to Galaxy and see its workflow link", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const docName = await editor.header.getName();

    const picker = await editor.openGalleryPicker();
    await picker.goToSection("Documents");
    await picker.uploadFile(ATTACHMENT_PATH, ATTACHMENT_NAME);
    await picker.selectItem(ATTACHMENT_NAME);
    await picker.add();

    await editor.editToolbar.save();

    const galaxyDialog = await editor.openGalaxyDialog();
    await galaxyDialog.selectServerAndAllFiles(GALAXY_ALIAS);
    await galaxyDialog.upload();

    await expect.poll(() => galaxyDialog.getResultText()).toContain(`RSPACE_${docName}`);

    await galaxyDialog.close();

    await expect(editor.galaxyWorkflowIcon).toBeVisible();

    const workflowDialog = await editor.openExternalWorkflowsDialog();

    await expect(workflowDialog.columnHeader("Data Uploaded")).toBeVisible();
    await expect(workflowDialog.columnHeader("Container/Galaxy History")).toBeVisible();
    await expect(workflowDialog.columnHeader("Invocation Status")).toBeVisible();

    await expect(workflowDialog.dataRow(ATTACHMENT_LINK_NAME)).toBeVisible();
    await expect(workflowDialog.historyLink(ATTACHMENT_LINK_NAME, `RSPACE_${docName}`)).toBeVisible();
    await workflowDialog.close();
  });
});
