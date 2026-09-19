import type { Locator, Page } from "@playwright/test";

export class WorkspaceTemplatePickerDialog {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Select a template" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  // Legacy jQuery UI tabs widget
  async switchToSharedWithMe(): Promise<void> {
    await this.page.getByRole("tab", { name: "Shared With Me" }).click();
    await this.root.getByRole("tabpanel", { name: "Shared With Me" }).waitFor({ state: "visible" });
  }

  async createFromTemplate(templateName: string, newDocName: string): Promise<void> {
    await this.root
      .getByRole("row")
      .filter({ has: this.page.getByRole("cell", { name: templateName, exact: true }) })
      .click();
    await this.root.getByRole("textbox", { name: "New document name" }).fill(newDocName);
    await Promise.all([
      this.page.waitForURL("**/workspace/editor/structuredDocument/**"),
      this.root.getByRole("button", { name: "Create", exact: true }).click(),
    ]);
  }
}
