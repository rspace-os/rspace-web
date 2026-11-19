import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("My RSpace forms", () => {
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
    await form.field("GMO permit").showOnlyWhen("Is this a GMO?", ["Yes"]);
    await expect(form.field("Is this a GMO?").root).toContainText("Other fields depend on this field: GMO permit");

    const forms = await form.saveAndClose();
    const reopened = await forms.edit(formName);
    await expect(reopened.field("GMO permit").conditionSource).toHaveText("Is this a GMO?");
    await expect(reopened.field("GMO permit").displayCondition.getByRole("button", { name: "Yes" })).toBeVisible();
  });

  test("As a user, publishing blocks the page while the fields save, and a second click does nothing", async ({
    page,
    pageMyRSpace,
  }) => {
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.addField("String", "slow field");
    let fieldSaves = 0;
    await page.route("**/workspace/editor/form/ajax/fields?**", async (route) => {
      if (route.request().method() !== "POST") return route.fallback();
      fieldSaves++;
      await new Promise((resolve) => setTimeout(resolve, 1500));
      return route.fallback();
    });

    await page.getByRole("button", { name: "Publish" }).click();
    await expect(form.pageBlocker).toBeVisible();
    await page.getByRole("button", { name: "Publish" }).click({ force: true });

    await expect(page.getByRole("dialog").filter({ has: page.locator("#publishShareDlg") })).toBeVisible();
    expect(fieldSaves).toBe(1);
  });

  test("As a user, I cannot edit fields while publishing, and can again if I cancel", async ({
    page,
    pageMyRSpace,
  }) => {
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.addField("String", "held field");
    const nameInputs = form.fields.root.getByRole("textbox", { name: "Name", exact: true });
    const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
    let releaseDialog: () => void = () => {};
    let holdDialog = true;
    let dialogHeld = false;
    await page.route("**/workspace/editor/form/ajax/publishAndShare?**", async (route) => {
      if (route.request().method() === "GET") {
        if (holdDialog) {
          await new Promise<void>((resolve) => {
            releaseDialog = resolve;
            dialogHeld = true;
          });
        }
      } else {
        await delay(1500);
      }
      return route.fallback();
    });
    const shareDialog = page.getByRole("dialog").filter({ has: page.locator("#publishShareDlg") });

    // while the access dialog loads
    await page.getByRole("button", { name: "Publish" }).click();
    await expect(nameInputs).toHaveCount(0);
    await expect.poll(() => dialogHeld).toBe(true);
    releaseDialog();
    await expect(shareDialog).toBeVisible();
    await expect(nameInputs).toHaveCount(0);
    await shareDialog.getByRole("button", { name: "Cancel" }).click();
    await expect(nameInputs).toHaveCount(1);

    // while the publish request runs, and after it
    holdDialog = false;
    await page.getByRole("button", { name: "Publish" }).click();
    await shareDialog.getByRole("button", { name: "OK" }).click();
    await expect(nameInputs).toHaveCount(0);
    await expect(page.getByText(/This form is published\./)).toBeVisible();
    await expect(nameInputs).toHaveCount(0);
  });

  test("As a user, I can edit fields again and retry when publishing fails", async ({ page, pageMyRSpace }) => {
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.addField("String", "retried field");
    const nameInputs = form.fields.root.getByRole("textbox", { name: "Name", exact: true });
    const failNext = { GET: true, POST: true };
    await page.route("**/workspace/editor/form/ajax/publishAndShare?**", (route) => {
      const method = route.request().method() as keyof typeof failNext;
      if (failNext[method]) {
        failNext[method] = false;
        return route.fulfill({ status: 500, body: "failed" });
      }
      return route.fallback();
    });
    const alerts: Array<string> = [];
    page.on("dialog", (dialog) => {
      alerts.push(dialog.type());
      void dialog.accept();
    });
    const shareDialog = page.getByRole("dialog").filter({ has: page.locator("#publishShareDlg") });

    // the access dialog fails to load
    await page.getByRole("button", { name: "Publish" }).click();
    await expect.poll(() => alerts).toEqual(["alert"]);
    await expect(nameInputs).toHaveCount(1);
    await expect(shareDialog).toBeHidden();

    // the publish request fails
    await page.getByRole("button", { name: "Publish" }).click();
    await shareDialog.getByRole("button", { name: "OK" }).click();
    await expect.poll(() => alerts).toEqual(["alert", "alert"]);
    await expect(nameInputs).toHaveCount(1);
    await expect(page.getByRole("button", { name: "Publish" })).toBeVisible();

    await page.getByRole("button", { name: "Publish" }).click();
    await shareDialog.getByRole("button", { name: "OK" }).click();
    await expect(page.getByText(/This form is published\./)).toBeVisible();
  });

  test("As a user, a failed Save and Close still warns before I leave", async ({ page, pageMyRSpace }) => {
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.addField("String", "kept field");
    await page.route("**/workspace/editor/form/ajax/saveForm", (route) =>
      route.fulfill({ status: 500, body: "server error" }),
    );

    const fieldsSaved = page.waitForResponse(
      (response) => response.url().includes("/ajax/fields") && response.request().method() === "POST",
    );
    await page.getByRole("button", { name: "Save and Close" }).click();
    expect((await fieldsSaved).status()).toBe(200);
    await page.waitForResponse((response) => response.url().includes("/ajax/saveForm"));

    const leaving = page.waitForEvent("dialog");
    await page.close({ runBeforeUnload: true });
    const dialog = await leaving;
    expect(dialog.type()).toBe("beforeunload");
    await dialog.dismiss();
  });

  test("As a user, Revert asks before discarding the draft even when the fields fail to load", async ({
    page,
    pageMyRSpace,
  }) => {
    const formName = uniqueName("e2e-revert");
    await pageMyRSpace.open();
    const form = await pageMyRSpace.openCreateForm();
    await form.rename(formName);
    await form.addField("String", "a field");
    await form.publishWithoutSharing();
    const forms = await form.saveAndClose();

    await page.route("**/workspace/editor/form/ajax/fields?**", (route) => route.fulfill({ status: 500 }));
    let discards = 0;
    page.on("request", (request) => {
      if (request.url().includes("/ajax/abandonUpdateForm")) discards++;
    });
    await forms.formRow(formName).getByRole("link", { name: formName, exact: true }).click();
    await expect(page.getByText("The fields of this form could not be loaded.")).toBeVisible();

    await page.getByRole("button", { name: "Revert" }).click();
    const confirm = page.getByRole("dialog", { name: "Abandon the changes to this form?" });
    await confirm.getByRole("button", { name: "Cancel" }).click();

    await expect(confirm).toBeHidden();
    expect(discards).toBe(0);
    await expect(page).toHaveURL(/\/workspace\/editor\/form\/edit\//);
  });

  // Known bug: PRT-1148, the Manage Forms access dialog. Unskip once fixed.
  test.skip("As a user, I can publish a form, add it to the create menu, find it, and duplicate it", async ({
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
