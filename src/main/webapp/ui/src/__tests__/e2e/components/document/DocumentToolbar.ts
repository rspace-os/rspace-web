import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";

/**
 * Edit-mode toolbar. Save, Cancel, and (via `actions`) Export/Print/Close
 * live here. View-mode-only actions (Sign, Save as Template) are in
 * `DocumentViewToolbar`.
 *
 */
export class DocumentToolbar {
  readonly saveMenuButton: Locator;
  readonly cancelButton: Locator;
  readonly actions: ToolbarCommonActions;

  constructor(private readonly page: Page) {
    this.saveMenuButton = page.getByTestId("notebook-save-btn");
    this.cancelButton = page.getByTestId("notebooktoolbar-cancel");
    this.actions = new ToolbarCommonActions(page);
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
