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

  async documentNames(): Promise<string[]> {
    const rows = this.table.getByRole("row");
    const names: string[] = [];
    for (let index = 1; index < (await rows.count()); index++) {
      names.push((await rows.nth(index).getByRole("link").nth(1).innerText()).trim());
    }
    return names;
  }

  async rowCount(): Promise<number> {
    return (await this.table.getByRole("row").count()) - 1;
  }

  async uniqueIdAt(index: number): Promise<string> {
    return (
      await this.table
        .getByRole("row")
        .nth(index + 1)
        .getByRole("cell")
        .nth(1)
        .innerText()
    ).trim();
  }

  async sharedWithAt(index: number): Promise<string> {
    return (
      await this.table
        .getByRole("row")
        .nth(index + 1)
        .getByRole("cell")
        .nth(2)
        .innerText()
    ).trim();
  }

  async openRecordInfo(name: string): Promise<RecordInfoDialog> {
    const row = this.table.getByRole("row").filter({ has: this.page.getByRole("link", { name, exact: true }) });
    await row.getByRole("link", { name: "Record Info" }).click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
