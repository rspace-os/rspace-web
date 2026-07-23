import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";

export class DocumentToolbar {
  readonly saveMenuButton: Locator;
  readonly cancelButton: Locator;
  readonly actions: ToolbarCommonActions;

  constructor(private readonly page: Page) {
    const toolbar = page.locator("#toolbar2");
    this.saveMenuButton = toolbar.getByRole("button", { name: "Save", exact: true });
    this.cancelButton = toolbar.getByRole("button", { name: "Cancel", exact: true });
    this.actions = new ToolbarCommonActions(page);
  }

  async save(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByRole("menuitem", { name: "Save", exact: true }).click();
  }

  async saveAndView(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByRole("menuitem", { name: "Save & View", exact: true }).click();
  }

  async saveAndClose(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByRole("menuitem", { name: "Save & Close", exact: true }).click();
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
