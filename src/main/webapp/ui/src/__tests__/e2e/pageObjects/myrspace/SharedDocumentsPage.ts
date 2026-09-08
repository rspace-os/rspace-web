import type { Locator } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { BasePage } from "../BasePage";

export class SharedDocumentsPage extends BasePage {
  readonly path = "/record/share/manage";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Shared Documents" }).waitFor({ state: "visible" });
  }

  get table(): Locator {
    return this.page.getByRole("table");
  }

  async search(query: string): Promise<void> {
    await this.page.getByRole("textbox", { name: "By document or user" }).fill(query);
    await this.page.getByRole("button", { name: "Search", exact: true }).click();
    await this.page.getByRole("button", { name: "Clear search" }).waitFor({ state: "visible" });
  }

  async sortByDocumentName(): Promise<void> {
    await this.table.getByRole("link", { name: "Document name" }).click();
    await this.page.waitForLoadState("networkidle");
  }

  async nextPage(): Promise<void> {
    await this.page.getByRole("link", { name: "2", exact: true }).click();
    await this.page.waitForLoadState("networkidle");
  }

  get dataRows(): Locator {
    return this.table.locator("tbody").getByRole("row");
  }

  private async columnIndex(headerName: string): Promise<number> {
    const headers = this.table.getByRole("columnheader");
    const count = await headers.count();
    for (let i = 0; i < count; i++) {
      if ((await headers.nth(i).innerText()).trim() === headerName) return i;
    }
    throw new Error(`columnIndex: no "${headerName}" column header found`);
  }

  async documentNames(): Promise<string[]> {
    const rows = this.dataRows;
    const names: string[] = [];
    for (let index = 0; index < (await rows.count()); index++) {
      names.push((await rows.nth(index).locator("a:not(.recordInfoIcon)").first().innerText()).trim());
    }
    return names;
  }

  async rowCount(): Promise<number> {
    return this.dataRows.count();
  }

  async uniqueIdAt(index: number): Promise<string> {
    return (await this.dataRows.nth(index).locator('a[href^="/globalId/"]').innerText()).trim();
  }

  async sharedWithAt(index: number): Promise<string> {
    const columnIndex = await this.columnIndex("Shared with");
    return (await this.dataRows.nth(index).getByRole("cell").nth(columnIndex).innerText()).trim();
  }

  async openRecordInfo(name: string): Promise<RecordInfoDialog> {
    const row = this.dataRows.filter({ has: this.page.getByRole("link", { name, exact: true }) });
    await row.getByRole("link", { name: "Record Info" }).click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
