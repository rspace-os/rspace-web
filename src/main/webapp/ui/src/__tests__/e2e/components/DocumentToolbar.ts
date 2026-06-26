import type { Locator, Page } from "@playwright/test";

/**
 * Edit-mode toolbar. Save, Cancel, Export, and Close live here.
 * View-mode actions (Delete, Sign, Save as Template) are in DocumentViewToolbar.
 */
export class DocumentToolbar {
  readonly saveMenuButton: Locator;
  readonly cancelButton: Locator;
  readonly exportButton: Locator;
  readonly closeLink: Locator;

  constructor(private readonly page: Page) {
    this.saveMenuButton = page.getByTestId("notebook-save-btn");
    this.cancelButton = page.getByTestId("notebooktoolbar-cancel");
    this.exportButton = page.getByTestId("notebooktoolbar-export");
    this.closeLink = page.getByTestId("structured-document-back");
  }

  async save(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByTestId("save-btn-save").click();
  }

  async saveAndView(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByTestId("save-btn-view").click();
  }

  async saveAndClose(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByTestId("save-btn-close").click();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.page.getByRole("button", { name: "Yes, cancel" }).click();
  }

  async cancelAndStay(): Promise<void> {
    await this.cancelButton.click();
    await this.page.getByRole("button", { name: "No, don't" }).click();
  }
}
