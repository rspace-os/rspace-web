import { expect, type Locator, type Page } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";
import { BasePage } from "../BasePage";

export type SharedRecordPermission = "READ" | "EDIT";

export class SharedDocumentsPage extends BasePage {
  readonly path = "/record/share/manage";

  readonly searchInput: Locator;
  readonly searchButton: Locator;

  constructor(page: Page) {
    super(page);
    this.searchInput = page.getByRole("textbox", { name: "By document or user" });
    this.searchButton = page.getByRole("button", { name: "Search", exact: true });
  }

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Shared Documents" }).waitFor({ state: "visible" });
  }

  /** Alias for {@link waitUntilLoaded}. */
  async isLoaded(): Promise<void> {
    await this.waitUntilLoaded();
  }

  override async open(): Promise<void> {
    await super.open();
    await this.waitUntilLoaded();
  }

  get table(): Locator {
    return this.page.getByRole("table");
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await this.searchButton.click();
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

  /** A row is uniquely identified by record name + recipient, since the same record can be shared with several recipients. */
  row(recordName: string, sharedWith: string): Locator {
    return this.page
      .getByRole("row")
      .filter({ has: this.page.getByRole("link", { name: recordName, exact: true }) })
      .filter({ hasText: sharedWith });
  }

  async isListed(recordName: string, sharedWith: string): Promise<boolean> {
    return (await this.row(recordName, sharedWith).count()) > 0;
  }

  async getPermission(recordName: string, sharedWith: string): Promise<SharedRecordPermission> {
    const label = await this.row(recordName, sharedWith)
      .getByRole("combobox", { name: "Permission" })
      .locator("option:checked")
      .innerText();
    return label.trim() as SharedRecordPermission;
  }

  async setPermission(recordName: string, sharedWith: string, permission: SharedRecordPermission): Promise<void> {
    const select = this.row(recordName, sharedWith).getByRole("combobox", { name: "Permission" });
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname === "/record/share/permissions"),
      select.selectOption({ label: permission }),
    ]);
    if (!response.ok()) {
      throw new Error(`Changing permission for "${recordName}" failed: ${response.status()} ${response.statusText()}`);
    }
    const failure = await response.finished();
    if (failure) throw failure;

    const result = (await response.json()) as { success: boolean; data: string | null };
    expect(result, `Changing permission for "${recordName}" succeeds`).toMatchObject({
      success: true,
      data: "Updated",
    });
    await this.page.locator('[data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
    await expect(select.locator("option:checked")).toHaveText(permission);
  }

  async unshare(recordName: string, sharedWith: string): Promise<void> {
    const target = this.row(recordName, sharedWith);
    await target.getByRole("link", { name: "Unshare" }).click();
    await target.waitFor({ state: "hidden" });
  }
}
