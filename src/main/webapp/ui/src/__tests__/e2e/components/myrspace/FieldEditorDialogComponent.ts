import type { Locator, Page } from "@playwright/test";

export type FormFieldType = "Number" | "String" | "Text" | "Radio" | "Choice" | "Date" | "Time";

export class FieldEditorDialogComponent {
  readonly root: Locator;
  private readonly optionNames: string[] = [];

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Field Editor" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private get fieldGroup(): Locator {
    return this.root.getByRole("group", { name: "Edit field" });
  }

  async selectType(type: FormFieldType): Promise<void> {
    await this.root.getByRole("combobox", { name: "Field Type" }).selectOption(type);
    await this.fieldGroup.waitFor({ state: "visible" });
  }

  async setName(name: string): Promise<void> {
    await this.fieldGroup.getByRole("textbox", { name: "Name", exact: true }).fill(name);
  }

  async setRequired(required: boolean): Promise<void> {
    await this.fieldGroup.getByRole("checkbox").last().setChecked(required);
  }

  async fillNumberValues(values: { defaultValue?: string; min?: string; max?: string }): Promise<void> {
    if (values.defaultValue !== undefined) {
      await this.fieldGroup.getByRole("textbox", { name: "Default Value" }).fill(values.defaultValue);
    }
    if (values.min !== undefined) {
      await this.fieldGroup.getByRole("textbox", { name: "Min Value" }).fill(values.min);
    }
    if (values.max !== undefined) {
      await this.fieldGroup.getByRole("textbox", { name: "Max Value" }).fill(values.max);
    }
  }

  async fillTextDefault(value: string): Promise<void> {
    await this.fieldGroup.getByRole("textbox", { name: "Default Value" }).fill(value);
  }

  async addOption(option: string): Promise<void> {
    await this.fieldGroup.getByRole("textbox").nth(1).fill(option);
    await this.fieldGroup.getByRole("button", { name: "Add New" }).click();
    this.optionNames.push(option);
  }

  async selectDefaultOption(type: "Radio" | "Choice", option: string): Promise<void> {
    const role = type === "Radio" ? "radio" : "checkbox";
    const optionIndex = this.optionNames.indexOf(option);
    if (optionIndex === -1) throw new Error(`Option '${option}' was not added through this field editor.`);
    await this.fieldGroup.getByRole(role).nth(optionIndex).check();
  }

  async setSortAlphabetically(checked: boolean): Promise<void> {
    await this.fieldGroup.getByLabel("Sort alphabetically").setChecked(checked);
  }

  async setShowAsPicklist(checked: boolean): Promise<void> {
    await this.fieldGroup.getByLabel("Display as a picklist?").setChecked(checked);
  }

  async uploadOptionsFile(filePath: string, optionNames: string[] = []): Promise<void> {
    await this.fieldGroup.getByLabel("Or upload from a file").setInputFiles(filePath);
    await this.fieldGroup.getByRole("button", { name: "Read file" }).click();
    this.optionNames.push(...optionNames);
  }

  async save(): Promise<void> {
    await this.root.getByRole("button", { name: "Save", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async saveExpectingValidationError(): Promise<void> {
    await this.root.getByRole("button", { name: "Save", exact: true }).click();
    await this.root.getByRole("img", { name: "Warning" }).waitFor({ state: "visible" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
