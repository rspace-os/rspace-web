import type { Locator } from "@playwright/test";
import {
  FieldEditorDialogComponent,
  type FormFieldType,
} from "@/__tests__/e2e/components/myrspace/FieldEditorDialogComponent";
import { FormAccessDialogComponent } from "@/__tests__/e2e/components/myrspace/FormAccessDialogComponent";
import { ReorderFieldsDialogComponent } from "@/__tests__/e2e/components/myrspace/ReorderFieldsDialogComponent";
import { BasePage } from "../BasePage";
import { ManageFormsPage } from "./ManageFormsPage";

export class CreateFormPage extends BasePage {
  readonly path = "/workspace/editor/form/";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("button", { name: "Save and Close" }).waitFor({ state: "visible" });
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

  async openFieldEditor(type: FormFieldType): Promise<FieldEditorDialogComponent> {
    await this.page.getByRole("button", { name: "Add Field" }).click();
    const editor = new FieldEditorDialogComponent(this.page);
    await editor.waitUntilVisible();
    await editor.selectType(type);
    return editor;
  }

  async addField(type: FormFieldType, name: string, required = false): Promise<void> {
    const editor = await this.openFieldEditor(type);
    await editor.setName(name);
    await editor.setRequired(required);
    if (type === "Text") {
      await editor.fillTextDefault("def");
    }
    if (type === "Radio" || type === "Choice") {
      await editor.addOption("Option 1");
    }
    await editor.save();
    await this.fieldRow(name).waitFor({ state: "visible" });
  }

  fieldRow(name: string): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByText(name, { exact: false }) });
  }

  fieldRowAt(index: number): Locator {
    return this.page.getByRole("row").nth(index + 1);
  }

  async openFieldForEditing(name: string): Promise<FieldEditorDialogComponent> {
    await this.fieldRow(name).getByRole("button", { name: "Edit" }).click();
    const editor = new FieldEditorDialogComponent(this.page);
    await editor.waitUntilVisible();
    return editor;
  }

  async reorderFields(): Promise<ReorderFieldsDialogComponent> {
    await this.page.getByRole("button", { name: "Reorder Fields" }).click();
    const dialog = new ReorderFieldsDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async publish(): Promise<FormAccessDialogComponent> {
    await this.page.getByRole("button", { name: "Publish" }).click();
    const dialog = new FormAccessDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async deleteField(name: string): Promise<void> {
    await this.fieldRow(name).getByRole("button", { name: "Delete" }).click();
    await this.fieldRow(name).waitFor({ state: "hidden" });
  }

  async revert(): Promise<void> {
    await this.page.getByRole("button", { name: "Revert" }).click();
  }

  async update(): Promise<void> {
    await this.page.getByRole("button", { name: "Update" }).click();
  }

  async saveAndClose(): Promise<ManageFormsPage> {
    await this.page.getByRole("button", { name: "Save and Close" }).click();
    await this.page.waitForURL((url) => url.pathname === "/workspace/editor/form/list");
    const page = new ManageFormsPage(this.page);
    await page.waitUntilLoaded();
    return page;
  }
}
