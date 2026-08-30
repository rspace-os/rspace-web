import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("My RSpace forms", () => {
  test("As a user, number fields validate names, ranges, and default values", async ({ pageMyRSpace }) => {
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    const editor = await form.openFieldEditor("Number");

    await editor.setName("a".repeat(51));
    await editor.saveExpectingValidationError();
    await expect(editor.root).toBeVisible();

    await editor.setName("measurement");
    await editor.fillNumberValues({ min: "10", max: "0" });
    await editor.saveExpectingValidationError();
    await expect(editor.root).toBeVisible();

    await editor.fillNumberValues({ defaultValue: "10", min: "15", max: "" });
    await editor.saveExpectingValidationError();
    await expect(editor.root).toBeVisible();

    await editor.fillNumberValues({ defaultValue: "10", min: "", max: "5" });
    await editor.saveExpectingValidationError();
    await expect(editor.root).toBeVisible();

    await editor.fillNumberValues({ defaultValue: "", min: "", max: "" });
    await editor.save();
    await expect(form.fieldRow("measurement")).toBeVisible();
  });

  test("As a user, I can create a form that supports every field type and reorder its fields", async ({
    pageMyRSpace,
  }) => {
    const formName = uniqueName("e2e-all-fields");
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.rename(formName);

    await form.addField("String", "short text");
    await form.addField("Text", "long text");
    await form.addField("Number", "amount");
    await form.addField("Radio", "radio choice");
    await form.addField("Choice", "multiple choice");
    await form.addField("Date", "sample date");
    await form.addField("Time", "sample time");

    const reorder = await form.reorderFields();
    await reorder.select("sample time", "Time");
    await reorder.move("Top");
    await reorder.done();

    await expect(form.fieldRowAt(0)).toContainText("sample time");
    const forms = await form.saveAndClose();
    await expect(forms.formRow(formName)).toBeVisible();
  });

  test("As a user, I can publish a form, add it to the create menu, find it, and duplicate it", async ({
    pageMyRSpace,
  }) => {
    const formName = uniqueName("e2e-form-operations");
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.rename(formName);
    await form.addField("String", "required value", true);
    let forms = await form.saveAndClose();

    await forms.search(formName);
    await expect(forms.formRow(formName)).toBeVisible();
    await forms.configureAccess(formName, "READ", "NONE");
    await expect(forms.status(formName)).toContainText("PUBLISHED");

    await forms.toggleMenu(formName, "Add to Menu");
    await forms.duplicate(formName);
    await forms.search(formName);
    await expect(forms.formRow(formName)).toBeVisible();

    await pageMyRSpace.open();
    forms = await pageMyRSpace.openManageForms();
    await forms.search(formName);
    await expect(forms.formRow(formName)).toBeVisible();
  });
});
