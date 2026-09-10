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
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/saveStructuredDocument")),
      this.page.getByRole("menuitem", { name: "Save", exact: true }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Save failed: ${response.status()} ${response.statusText()}`);
    }
  }

  async saveAndView(): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByRole("menuitem", { name: "Save & View", exact: true }).click();
  }

  async saveAndClose(): Promise<void> {
    await this.saveMenuButton.click();
    await Promise.all([
      this.page.waitForURL((url) => !url.pathname.includes("/workspace/editor/structuredDocument")),
      this.page.getByRole("menuitem", { name: "Save & Close", exact: true }).click(),
    ]);
  }

  async saveAndNew(): Promise<void> {
    await this.saveAndNavigate("Save & New");
  }

  async saveAndClone(): Promise<void> {
    await this.saveAndNavigate("Save & Clone");
  }

  /** Save-menu items that leave the current document, landing on a different URL. */
  private async saveAndNavigate(menuItemName: "Save & New" | "Save & Clone"): Promise<void> {
    await this.saveMenuButton.click();
    const before = this.page.url();
    await Promise.all([
      this.page.waitForURL((url) => url.toString() !== before),
      this.page.getByRole("menuitem", { name: menuItemName, exact: true }).click(),
    ]);
  }

  async saveAsTemplate(templateName: string): Promise<void> {
    await this.saveMenuButton.click();
    await this.page.getByRole("menuitem", { name: "Save as Template", exact: true }).click();
    const dialog = this.page.getByRole("dialog", { name: "Save Template" });
    await dialog.getByRole("textbox", { name: "Template Name" }).fill(templateName);
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/workspace/editor/structuredDocument/saveTemplate")),
      dialog.getByRole("button", { name: "OK" }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Save as Template failed: ${response.status()} ${response.statusText()}`);
    }
    await dialog.waitFor({ state: "hidden" });
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
