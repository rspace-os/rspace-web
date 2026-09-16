import { type Locator, page } from "vitest/browser";

/**
 * Page object for PidinstImportDialog as mounted by PidinstImportDialog.story.tsx. Locators and
 * user actions only; assertions live in the spec.
 */
export class PidinstImportDialogPage {
  get dialog(): Locator {
    return page.getByRole("dialog", { name: "Import Instrument from PIDINST" });
  }

  get searchField(): Locator {
    return page.getByRole("textbox", { name: "Search the registry" });
  }

  get searchButton(): Locator {
    return page.getByRole("button", { name: "Search", exact: true });
  }

  get importButton(): Locator {
    return page.getByRole("button", { name: "Import", exact: true });
  }

  recordRadio(name: string): Locator {
    return page.getByRole("radio", { name: `Select record: ${name}` });
  }

  successAlert(): Locator {
    return page.getByRole("alert").filter({ hasText: "Successfully imported the instrument." });
  }

  errorAlert(): Locator {
    return page.getByRole("alert").filter({ hasText: "Could not import the instrument." });
  }

  /** The warning the Import button raises in its popover when the selection cannot be imported. */
  validationAlert(text: string): Locator {
    return page.getByRole("alert").filter({ hasText: text });
  }

  subMessageToggle(count: number): Locator {
    return page.getByRole("button", { name: `${count} sub-messages. Toggle to show` });
  }

  async search(query: string): Promise<void> {
    await this.searchField.fill(query);
    await this.searchButton.click();
  }

  async selectRecord(name: string): Promise<void> {
    await this.recordRadio(name).click();
  }

  async clickImport(): Promise<void> {
    await this.importButton.click();
  }
}
