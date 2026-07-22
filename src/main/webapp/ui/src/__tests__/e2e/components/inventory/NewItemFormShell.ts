import type { Locator, Page } from "@playwright/test";
import { CollapsibleSections } from "./CollapsibleSections";
import { CustomFieldsEditor } from "./CustomFieldsEditor";

export class NewItemFormShell {
  readonly root: Locator;
  private readonly nameInput: Locator;
  readonly saveButton: Locator;
  private readonly sections: CollapsibleSections;

  constructor(
    private readonly page: Page,
    private readonly headingName: string,
  ) {
    this.root = page.getByRole("main");
    this.nameInput = this.root.getByRole("textbox", { name: "Name" });
    this.saveButton = this.root.getByRole("button", { name: "Save", exact: true });
    this.sections = new CollapsibleSections(this.root);
  }

  async waitForOpen(): Promise<void> {
    await this.root.getByRole("heading", { level: 2, name: this.headingName }).waitFor({ state: "visible" });
  }

  async fillName(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  section(name: string): Locator {
    return this.sections.section(name);
  }

  async isSectionExpanded(name: string): Promise<boolean> {
    return this.sections.isExpanded(name);
  }

  async expandSection(name: string): Promise<void> {
    await this.sections.expand(name);
  }

  async isSaveEnabled(): Promise<boolean> {
    return await this.saveButton.isEnabled();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
    await this.saveButton.waitFor({ state: "detached" });
  }

  customFields(): CustomFieldsEditor {
    return new CustomFieldsEditor(this.page, this.section("Custom Fields"));
  }
}

export function delegateToForm(form: NewItemFormShell) {
  return {
    waitForOpen: () => form.waitForOpen(),
    fillName: (name: string) => form.fillName(name),
    section: (name: string) => form.section(name),
    isSectionExpanded: (name: string) => form.isSectionExpanded(name),
    expandSection: (name: string) => form.expandSection(name),
    isSaveEnabled: () => form.isSaveEnabled(),
    save: () => form.save(),
    customFields: () => form.customFields(),
  };
}
