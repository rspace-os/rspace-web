import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

// Known bug:  PRT-1148. Unskip once fixed.
test.describe
  .skip("My RSpace forms", () => {
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
      await expect(forms.formRow(formName)).toHaveCount(2);

      await pageMyRSpace.open();
      forms = await pageMyRSpace.openManageForms();
      await forms.search(formName);
      await expect(forms.formRow(formName)).toHaveCount(2);
    });
  });

test.describe("My RSpace Choice fields", () => {
  test("As a form author, a single-selection Choice field offers radio buttons and keeps exactly one default", async ({
    clientForms,
    pageCreateForm,
  }) => {
    const options = ["a", "b", "c"];
    const form = await clientForms.create({
      name: uniqueName("ChoiceEditorForm"),
      fields: [
        { name: "SingleChoice", type: "Choice", options, defaultOptions: ["a"], multipleChoice: false },
        { name: "MultiChoice", type: "Choice", options, defaultOptions: ["a"], multipleChoice: true },
      ],
    });
    await pageCreateForm.openExisting(form.id);
    await expect(pageCreateForm.fieldPreview("SingleChoice").getByRole("radio")).toHaveCount(3);
    await expect(pageCreateForm.fieldPreview("MultiChoice").getByRole("checkbox")).toHaveCount(3);

    const editor = await pageCreateForm.openFieldForEditing("SingleChoice");
    await expect(editor.choiceDefaultOption("radio", "a")).toBeChecked();
    await editor.setMultipleChoice(true);
    await editor.choiceDefaultOption("checkbox", "b").check();
    // Switching back to single with two defaults keeps both ticked until the author picks one.
    await editor.setMultipleChoice(false);
    await expect(editor.choiceDefaultOption("checkbox", "a")).toBeChecked();
    await expect(editor.choiceDefaultOption("checkbox", "b")).toBeChecked();
    await editor.saveExpectingValidationError();
    await expect(editor.root).toContainText("A single-selection field can have only one default option.");

    await editor.choiceDefaultOption("checkbox", "c").check();
    await editor.choiceDefaultOption("checkbox", "a").uncheck();
    await expect(editor.choiceDefaultOption("checkbox", "b")).toBeChecked();
    await expect(editor.choiceDefaultOption("checkbox", "c")).toBeChecked();
    // Deleting one of the two ticked defaults leaves one, so the selectors become radios.
    await editor.deleteChoiceOption("c");
    await expect(editor.choiceDefaultOption("radio", "a")).not.toBeChecked();
    await expect(editor.choiceDefaultOption("radio", "b")).toBeChecked();
    await editor.save();
    const singlePreview = pageCreateForm.fieldPreview("SingleChoice");
    await expect(singlePreview.getByRole("radio")).toHaveCount(2);
    await expect(singlePreview.getByRole("radio", { name: "b", exact: true })).toBeChecked();
  });

  test("As a form author, a multiple-selection Choice field offers checkboxes for any number of defaults", async ({
    clientForms,
    pageCreateForm,
  }) => {
    const options = ["a", "b", "c"];
    const form = await clientForms.create({
      name: uniqueName("MultiChoiceEditorForm"),
      fields: [
        { name: "NoDefault", type: "Choice", options, multipleChoice: true },
        { name: "OneDefault", type: "Choice", options, defaultOptions: ["a"], multipleChoice: true },
        { name: "BecomesMulti", type: "Choice", options, defaultOptions: ["a"], multipleChoice: false },
      ],
    });
    await pageCreateForm.openExisting(form.id);
    const expectPreview = async (name: string, checked: string[]) => {
      const preview = pageCreateForm.fieldPreview(name);
      await expect(preview.getByRole("radio")).toHaveCount(0);
      await expect(preview.getByRole("checkbox")).toHaveCount(3);
      for (const option of options) {
        const input = preview.getByRole("checkbox", { name: option, exact: true });
        if (checked.includes(option)) await expect(input).toBeChecked();
        else await expect(input).not.toBeChecked();
      }
    };
    await expectPreview("NoDefault", []);
    await expectPreview("OneDefault", ["a"]);

    let editor = await pageCreateForm.openFieldForEditing("OneDefault");
    await expect(editor.choiceDefaultOption("checkbox", "a")).toBeChecked();
    await expect(editor.choiceDefaultOption("radio", "a")).toHaveCount(0);
    await editor.cancel();

    editor = await pageCreateForm.openFieldForEditing("NoDefault");
    for (const option of options) {
      await expect(editor.choiceDefaultOption("checkbox", option)).not.toBeChecked();
    }
    await editor.choiceDefaultOption("checkbox", "a").check();
    await editor.choiceDefaultOption("checkbox", "c").check();
    await editor.save();
    await expectPreview("NoDefault", ["a", "c"]);

    // A single field with one default shows radios; switching to multiple keeps that default as a ticked checkbox.
    editor = await pageCreateForm.openFieldForEditing("BecomesMulti");
    await expect(editor.choiceDefaultOption("radio", "a")).toBeChecked();
    await editor.setMultipleChoice(true);
    await expect(editor.choiceDefaultOption("checkbox", "a")).toBeChecked();
    await expect(editor.choiceDefaultOption("radio", "b")).toHaveCount(0);
    await editor.save();
    await expectPreview("BecomesMulti", ["a"]);
  });
});
