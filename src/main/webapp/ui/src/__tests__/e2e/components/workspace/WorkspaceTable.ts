import type { Locator, Page } from "@playwright/test";
import { WorkspaceRecordInfoDialog } from "./WorkspaceRecordInfoDialog";
import { WorkspaceSelectionBar } from "./WorkspaceSelectionBar";

export class WorkspaceTable {
  readonly root: Locator;
  private readonly selectionBar: WorkspaceSelectionBar;

  constructor(private readonly page: Page) {
    this.root = page.locator("#file_table");
    this.selectionBar = new WorkspaceSelectionBar(page);
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
    await this.selectRecords(name);
  }

  async selectRecords(...names: string[]): Promise<void> {
    for (const [i, name] of names.entries()) {
      await this.checkbox(name).check();
      if (i === 0) await this.selectionBar.waitUntilVisible();
    }
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
    await Promise.all([
      this.page.waitForResponse((res) => {
        const path = new URL(res.url()).pathname;
        return path.endsWith("/workspace/ajax/search") || path.includes("/workspace/ajax/view/");
      }),
      this.root.getByRole("columnheader", { name: column }).getByRole("link", { name: column }).click(),
    ]);
  }

  get dataRows(): Locator {
    return this.root.locator("tbody tr");
  }

  async rowCount(): Promise<number> {
    return this.dataRows.count();
  }
}
