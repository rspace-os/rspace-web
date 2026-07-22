import type { Locator, Page } from "@playwright/test";
import { WorkspaceRecordInfoDialog } from "./WorkspaceRecordInfoDialog";

export class WorkspaceTable {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("table");
  }

  row(name: string): Locator {
    return this.root.getByRole("row").filter({
      has: this.page.getByRole("link", { name, exact: true }),
    });
  }

  checkbox(name: string): Locator {
    return this.row(name).getByRole("checkbox", { name: "Select record" });
  }

  globalIdLink(name: string): Locator {
    return this.row(name).getByRole("link", { name: /^(SD|NB|FL)\d+$/ });
  }

  get selectAllCheckbox(): Locator {
    return this.root.getByRole("columnheader", { name: "Select/deselect all" }).getByRole("checkbox");
  }

  async selectRecord(name: string): Promise<void> {
    await this.checkbox(name).check();
  }

  async deselectRecord(name: string): Promise<void> {
    await this.checkbox(name).uncheck();
  }

  async openRecord(name: string): Promise<void> {
    await this.row(name).getByRole("link", { name, exact: true }).click();
  }

  async openInfoFor(name: string): Promise<WorkspaceRecordInfoDialog> {
    await this.row(name).getByRole("link", { name: "Record Info" }).click();
    const dialog = new WorkspaceRecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async sortBy(column: "Name" | "Created" | "Modified"): Promise<void> {
    await this.root.getByRole("columnheader", { name: column }).getByRole("link", { name: column }).click();
  }
}
