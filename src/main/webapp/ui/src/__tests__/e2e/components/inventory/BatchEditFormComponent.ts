import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export class BatchEditFormComponent {
  readonly root: Locator;
  /**
   * Every batch-editable field starts disabled, showing a nominal value (e.g. "Varies") rather
   * than its real input, with its own "Batch edit this field" checkbox to opt that field in -
   * otherwise saving would silently overwrite every selected record's field with the same value.
   * The Name field's actual textbox doesn't exist in the DOM at all until that checkbox is
   * checked (see BatchFormField's `disabled && !value` branch).
   */
  private readonly nameField: Locator;
  /**
   * The "Batch edit this field" checkbox only exists on BatchFormField's opt-in path - unlike the
   * "Name" group itself, it can't also match the single-record form that replaces this one once a
   * batch save succeeds and activates one of the saved records, so it's the right thing to wait on
   * for this form having actually closed.
   */
  private readonly nameFieldCheckbox: Locator;
  readonly saveButton: Locator;
  /** Sample-only: the "Sample requests" card in the Overview section (see Requestable.tsx). */
  private readonly requestableField: Locator;
  private readonly requestableFieldCheckbox: Locator;
  readonly requestableSwitch: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("main");
    this.nameField = this.root.getByRole("group", { name: "Name", exact: true });
    this.nameFieldCheckbox = this.nameField.getByRole("checkbox", { name: "Batch edit this field" });
    this.saveButton = this.root.getByRole("button").filter({
      has: page.getByText("Save", { exact: true }),
    });
    this.requestableField = this.root.getByRole("group", { name: "Sample requests" });
    this.requestableFieldCheckbox = this.requestableField.getByRole("checkbox", { name: "Batch edit this field" });
    this.requestableSwitch = this.requestableField.getByRole("switch", { name: "Requestable", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.nameField.waitFor({ state: "visible" });
  }

  async fillName(name: string): Promise<void> {
    await this.nameFieldCheckbox.check();
    await this.nameField.getByRole("textbox").fill(name);
  }

  /**
   * Opts the Sample requests field into batch editing (checking its own "Batch edit this field"
   * checkbox, which is what enables `requestableSwitch` in the first place) and sets the switch to
   * the given state, flipping it only if it doesn't already match.
   */
  async setRequestable(checked: boolean): Promise<void> {
    await this.requestableFieldCheckbox.check();
    if ((await this.requestableSwitch.isChecked()) !== checked) {
      await this.requestableSwitch.click();
    }
  }

  async save(): Promise<void> {
    await this.saveButton.evaluate((el) => el.scrollIntoView({ block: "center" }));
    await clickAndWaitDetached(this.saveButton, this.nameFieldCheckbox);
  }
}
