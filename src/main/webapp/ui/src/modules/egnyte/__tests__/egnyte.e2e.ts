import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { MOCK_EGNYTE_FILE_NAME, MOCK_EGNYTE_SECOND_FILE_NAME } from "./mock";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Egnyte integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode out of scope");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("egnyte.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("Egnyte", env.mockBaseUrl, {
      textboxName: "Egnyte Domain URL",
    });
  });

  test("As a user, I can insert Egnyte files once per selection after re-editing a document field", async ({
    pageWorkspace,
  }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const docView = await docEditor.saveAndView();
    const fieldName = "New List of Materials";
    const field = await docView.editField(fieldName);

    const dialog = docEditor.egnyteDialog;
    await field.clickToolbarButton("Insert from Egnyte");
    await dialog.waitForOpen();
    await dialog.waitForPicker();
    await dialog.selectFile(MOCK_EGNYTE_FILE_NAME);
    await dialog.confirmSelection();

    await expect(field.attachmentName(MOCK_EGNYTE_FILE_NAME)).toHaveCount(1);
    await field.saveAndFinishEditing();
    const savedField = await docView.getStructuredFieldValue(fieldName);
    await expect(savedField.getByRole("link", { name: MOCK_EGNYTE_FILE_NAME, exact: true })).toHaveCount(1);

    // Recreate the editor on the same page so a leaked document listener survives.
    const reopenedField = await docView.editField(fieldName);
    await expect(reopenedField.attachmentName(MOCK_EGNYTE_FILE_NAME)).toHaveCount(1);
    await reopenedField.body.press("ControlOrMeta+End");
    await reopenedField.body.press("ArrowRight");
    await reopenedField.clickToolbarButton("Insert from Egnyte");
    await dialog.waitForOpen();
    await dialog.waitForPicker();
    await dialog.selectFile(MOCK_EGNYTE_SECOND_FILE_NAME);
    await dialog.confirmSelection();

    await expect(reopenedField.attachmentName(MOCK_EGNYTE_SECOND_FILE_NAME)).toHaveCount(1);
    await expect(reopenedField.attachmentName(MOCK_EGNYTE_FILE_NAME)).toHaveCount(1);
    await reopenedField.saveAndFinishEditing();
    await expect(savedField.getByRole("link", { name: MOCK_EGNYTE_SECOND_FILE_NAME, exact: true })).toHaveCount(1);
    await expect(savedField.getByRole("link", { name: MOCK_EGNYTE_FILE_NAME, exact: true })).toHaveCount(1);
  });
});
