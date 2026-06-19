import { type Locator, page } from "vitest/browser";

/**
 * Page object for the FieldmarkImportDialog, as mounted by the story in
 * FieldmarkImportDialog.story.tsx. Encapsulates locators and user interactions;
 * assertions live in the tests themselves.
 */
export class FieldmarkImportDialogPage {
  get dialog(): Locator {
    return page.getByRole("dialog");
  }

  get dataGrid(): Locator {
    return page.getByRole("grid");
  }

  get importButton(): Locator {
    return page.getByRole("button", { name: "Import" });
  }

  notebookRadio(name: string): Locator {
    return page.getByRole("radio", { name: `Select notebook: ${name}` });
  }

  get igsnIdCombobox(): Locator {
    return page.getByRole("combobox", { name: "IGSN ID field" });
  }

  igsnOption(name: string): Locator {
    return page.getByRole("option", { name });
  }

  successAlert(): Locator {
    return page.getByRole("alert").filter({ hasText: "Successfully imported notebook" });
  }

  errorAlert(): Locator {
    return page.getByRole("alert").filter({ hasText: "Could not import notebook." });
  }

  subMessageToggle(count: number): Locator {
    return page.getByRole("button", {
      name: `${count} sub-messages. Toggle to show`,
    });
  }

  async selectNotebook(name: string): Promise<void> {
    await this.notebookRadio(name).click();
  }

  async clickImport(): Promise<void> {
    await this.importButton.click();
  }

  async selectIgsnOption(name: string): Promise<void> {
    await this.igsnIdCombobox.click();
    await this.igsnOption(name).click();
  }
}
