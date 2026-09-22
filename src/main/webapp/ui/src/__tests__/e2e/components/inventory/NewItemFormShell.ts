import type { Locator, Page } from "@playwright/test";
import { CollapsibleSections } from "./CollapsibleSections";
import { CustomFieldsEditor } from "./CustomFieldsEditor";

const SAVED_ENTITY_PATHS = ["containers", "samples", "instruments", "instrumentTemplates", "sampleTemplates"];

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

  // Save button detaches before the unsaved-changes guard actually clears -- that waits on
  // a GET re-fetch after edit-mode exit. Navigating too fast trips a spurious "Leave the
  // editor?" prompt, so this also waits for that re-fetch.
  async save(): Promise<void> {
    const refetchResponse = this.page.waitForResponse((r) => {
      if (r.request().method() !== "GET") return false;
      const [, api, inventory, v1, entity, id] = new URL(r.url()).pathname.split("/");
      return (
        api === "api" &&
        inventory === "inventory" &&
        v1 === "v1" &&
        SAVED_ENTITY_PATHS.includes(entity) &&
        id !== "" &&
        Number.isInteger(Number(id))
      );
    });
    await this.saveButton.click();
    await this.saveButton.waitFor({ state: "detached" });
    const response = await refetchResponse;
    if (!response.ok()) {
      throw new Error(`Post-save re-fetch failed: ${response.status()} ${response.statusText()}`);
    }
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
