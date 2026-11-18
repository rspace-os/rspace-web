import type { Locator } from "@playwright/test";
import { FormAccessDialogComponent } from "@/__tests__/e2e/components/myrspace/FormAccessDialogComponent";
import {
  type FormFieldCardComponent,
  FormFieldEditorComponent,
  type FormFieldType,
} from "@/__tests__/e2e/components/myrspace/FormFieldEditorComponent";
import { BasePage } from "../BasePage";
import { ManageFormsPage } from "./ManageFormsPage";

export class CreateFormPage extends BasePage {
  readonly path = "/workspace/editor/form/";

  get fields(): FormFieldEditorComponent {
    return new FormFieldEditorComponent(this.page);
  }

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("button", { name: "Save and Close" }).waitFor({ state: "visible" });
    await this.fields.waitUntilLoaded();
  }

  get statusNew(): Locator {
    return this.page.getByText("Status - NEW", { exact: true });
  }

  async rename(name: string): Promise<void> {
    await this.page.getByText("Name: Untitled", { exact: true }).click();
    const dialog = this.page.getByRole("dialog", { name: "Rename" });
    await dialog.getByRole("textbox", { name: "Please enter a new name" }).fill(name);
    await dialog.getByRole("button", { name: "Rename" }).click();
    await this.page.getByText(`Name: ${name}`, { exact: true }).waitFor({ state: "visible" });
  }

  /** Adds a field with what its type needs to be saved: an option, or default text. */
  async addField(type: FormFieldType, name: string, mandatory = false): Promise<FormFieldCardComponent> {
    const field = await this.fields.addField(type, name);
    await field.setMandatory(mandatory);
    if (type === "Text") await field.fillTextDefault("def");
    if (type === "Radio" || type === "Choice") await field.addOption("Option 1");
    return field;
  }

  field(name: string): FormFieldCardComponent {
    return this.fields.field(name);
  }

  fieldAt(index: number): FormFieldCardComponent {
    return this.fields.fieldAt(index);
  }

  async publish(): Promise<FormAccessDialogComponent> {
    await this.page.getByRole("button", { name: "Publish" }).click();
    const dialog = new FormAccessDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  /** Abandons the draft of a published form, after confirming. */
  async revert(): Promise<ManageFormsPage> {
    await this.page.getByRole("button", { name: "Revert" }).click();
    await this.page
      .getByRole("dialog", { name: "Abandon the changes to this form?" })
      .getByRole("button", { name: "Abandon changes" })
      .click();
    return this.formsList();
  }

  /** Saves the fields and makes the draft the new version of the form. */
  async update(): Promise<ManageFormsPage> {
    await this.page.getByRole("button", { name: "Update" }).click();
    return this.formsList();
  }

  async saveAndClose(): Promise<ManageFormsPage> {
    await this.page.getByRole("button", { name: "Save and Close" }).click();
    return this.formsList();
  }

  /** Save and Close when the fields fail a check: the page stays, with the problems shown. */
  async saveAndCloseExpectingProblems(): Promise<void> {
    await this.page.getByRole("button", { name: "Save and Close" }).click();
    await this.fields.saveProblems.waitFor({ state: "visible" });
  }

  private async formsList(): Promise<ManageFormsPage> {
    await this.page.waitForURL((url) => url.pathname === "/workspace/editor/form/list");
    const page = new ManageFormsPage(this.page);
    await page.waitUntilLoaded();
    return page;
  }
}
