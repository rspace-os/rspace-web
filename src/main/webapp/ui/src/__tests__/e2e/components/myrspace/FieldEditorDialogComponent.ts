import type { Locator, Page } from "@playwright/test";

export type FormFieldType = "Number" | "Text" | "String" | "Date" | "Time" | "Radio" | "Choice";

// The legacy jQuery UI "Field Editor" dialog
export class FieldEditorDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Field Editor" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private get fieldSection(): Locator {
    return this.root.locator("fieldset.form_field");
  }

  async selectType(type: FormFieldType): Promise<void> {
    await this.root.getByLabel("Field Type", { exact: true }).selectOption(type);
    await this.fieldSection.waitFor({ state: "visible" });
  }

  async setName(name: string): Promise<void> {
    await this.fieldSection.getByLabel("Name", { exact: true }).fill(name);
  }

  async setRequired(required: boolean): Promise<void> {
    const checkbox = this.fieldSection.locator("#mandatoryCheckbox");
    if (required) await checkbox.check();
    else await checkbox.uncheck();
  }

  async save(): Promise<void> {
    await this.root.getByRole("button", { name: "Save", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
