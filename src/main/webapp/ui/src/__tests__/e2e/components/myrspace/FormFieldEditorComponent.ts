import type { Locator, Page } from "@playwright/test";

export type FormFieldType = "Number" | "String" | "Text" | "Radio" | "Choice" | "Date" | "Time";

const TYPE_MENU_LABELS: Record<FormFieldType, string> = {
  Number: "Number",
  String: "String (one line of text)",
  Text: "Text (formatted text)",
  Radio: "Radio (one option)",
  Choice: "Choice (several options)",
  Date: "Date",
  Time: "Time",
};

/** One field of the form field editor: the shared field card with the ELN settings. */
export class FormFieldCardComponent {
  constructor(
    private readonly page: Page,
    readonly root: Locator,
  ) {}

  get nameInput(): Locator {
    return this.root.getByRole("textbox", { name: "Name", exact: true });
  }

  async setName(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async setMandatory(mandatory: boolean): Promise<void> {
    await this.root.getByRole("switch", { name: "Mandatory" }).setChecked(mandatory);
  }

  async addOption(option: string): Promise<void> {
    await this.root.getByRole("button", { name: "Add option" }).click();
    await this.root
      .getByRole("textbox", { name: /^Option \d+$/ })
      .last()
      .fill(option);
  }

  async fillNumberValues(values: { defaultValue?: string; min?: string; max?: string }): Promise<void> {
    if (values.min !== undefined) await this.root.getByRole("textbox", { name: "Minimum" }).fill(values.min);
    if (values.max !== undefined) await this.root.getByRole("textbox", { name: "Maximum" }).fill(values.max);
    if (values.defaultValue !== undefined) {
      await this.root.getByRole("textbox", { name: "Default value" }).fill(values.defaultValue);
    }
  }

  async fillTextDefault(value: string): Promise<void> {
    await this.root.getByRole("button", { name: /^Edit default text of/ }).click();
    const dialog = this.page.getByRole("dialog", { name: /^Default text of/ });
    await dialog.frameLocator("iframe").locator("body").fill(value);
    await dialog.getByRole("button", { name: "Done" }).click();
    await dialog.waitFor({ state: "hidden" });
  }

  /** The message a client check shows on this field. */
  problem(text: string): Locator {
    return this.root.getByText(text, { exact: true });
  }

  /** The field's "Display this field when…" editor. */
  get displayCondition(): Locator {
    return this.root.getByRole("group", { name: "Display this field when…" });
  }

  /** The field the display condition reads, as its select shows it. */
  get conditionSource(): Locator {
    return this.displayCondition.getByRole("combobox", { name: "Field", exact: true });
  }

  /** Shows the field only when `source` is any of `options`. */
  async showOnlyWhen(source: string, options: ReadonlyArray<string>): Promise<void> {
    await this.displayCondition.getByRole("radio", { name: "Only when another field has certain values" }).check();
    await this.conditionSource.click();
    await this.page.getByRole("option", { name: source, exact: true }).click();
    for (const option of options) {
      await this.displayCondition.getByRole("combobox", { name: "Options", exact: true }).click();
      await this.page.getByRole("option", { name: option, exact: true }).click();
      await this.page.keyboard.press("Escape");
    }
  }

  async moveToTop(): Promise<void> {
    await this.root.getByRole("button", { name: /to the top$/ }).click();
  }

  async delete(): Promise<void> {
    await this.root.getByRole("button", { name: /^Delete field / }).click();
  }
}

/** The React form field editor on the form editor page. */
export class FormFieldEditorComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.locator("#formFieldEditor");
  }

  async waitUntilLoaded(): Promise<void> {
    await this.root.getByRole("heading", { name: "Fields", level: 2 }).waitFor({ state: "visible" });
  }

  field(name: string): FormFieldCardComponent {
    return new FormFieldCardComponent(this.page, this.root.getByRole("region", { name, exact: true }));
  }

  /** The field at `index` in the form's order. */
  fieldAt(index: number): FormFieldCardComponent {
    return new FormFieldCardComponent(this.page, this.root.getByRole("region").nth(index));
  }

  /** Adds a field; the new field's name input has focus. */
  async addField(type: FormFieldType, name: string): Promise<FormFieldCardComponent> {
    await this.root.getByRole("button", { name: "Add field" }).click();
    await this.page.getByRole("menuitem", { name: TYPE_MENU_LABELS[type], exact: true }).click();
    await this.root.getByRole("textbox", { name: "Name", exact: true }).last().fill(name);
    return this.field(name);
  }

  /** The alert shown when the toolbar could not save the fields. */
  get saveProblems(): Locator {
    return this.root.getByRole("alert");
  }
}
