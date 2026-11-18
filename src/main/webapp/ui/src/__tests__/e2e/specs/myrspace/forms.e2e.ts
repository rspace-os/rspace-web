import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

// Known bug:  PRT-1148. Unskip once fixed.
test.describe
  .skip("My RSpace forms", () => {
    test("As a user, number fields validate names, ranges, and default values", async ({ pageMyRSpace }) => {
      await pageMyRSpace.open();
      const form = await pageMyRSpace.openCreateForm();
      const field = await form.fields.addField("Number", "a".repeat(51));
      await expect(field.problem("Use at most 50 characters.")).toBeVisible();
      await form.saveAndCloseExpectingProblems();

      await field.setName("measurement");
      const measurement = form.field("measurement");
      await measurement.fillNumberValues({ min: "10", max: "0" });
      await expect(measurement.problem("The minimum cannot be more than the maximum.")).toBeVisible();

      await measurement.fillNumberValues({ defaultValue: "10", min: "15", max: "" });
      await expect(measurement.problem("The default cannot be less than the minimum.")).toBeVisible();

      await measurement.fillNumberValues({ defaultValue: "10", min: "", max: "5" });
      await expect(measurement.problem("The default cannot be more than the maximum.")).toBeVisible();
      await form.saveAndCloseExpectingProblems();

      await measurement.fillNumberValues({ defaultValue: "", min: "", max: "" });
      // saving waits for the list of forms, so this only passes if the save went through
      await form.saveAndClose();
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
      const time = await form.addField("Time", "sample time");

      await time.moveToTop();
      await expect(form.fieldAt(0).nameInput).toHaveValue("sample time");
      const forms = await form.saveAndClose();
      await expect(forms.formRow(formName)).toBeVisible();

      const reopened = await forms.edit(formName);
      await expect(reopened.fieldAt(0).nameInput).toHaveValue("sample time");
    });

    test("As a user, I can show a field only when another field has a given answer, before the first save", async ({
      pageMyRSpace,
    }) => {
      const formName = uniqueName("e2e-display-rules");
      await pageMyRSpace.open();
      const form = await pageMyRSpace.openCreateForm();
      await form.rename(formName);

      const gmo = await form.fields.addField("Radio", "Is this a GMO?");
      await gmo.addOption("Yes");
      await gmo.addOption("No");
      await form.addField("String", "GMO permit", true);
      await form.fields.addRule("Is this a GMO?", ["Yes"], ["GMO permit"]);
      await expect(form.field("GMO permit").ruleSummary).toHaveText("Shown when Is this a GMO? is Yes");

      const forms = await form.saveAndClose();
      const reopened = await forms.edit(formName);
      await expect(reopened.field("GMO permit").ruleSummary).toHaveText("Shown when Is this a GMO? is Yes");
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
