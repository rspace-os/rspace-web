import { expect, type Locator, type Page } from "@playwright/test";
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

  async isLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Shared Documents" }).waitFor({ state: "visible" });
  }

  override async open(): Promise<void> {
    await super.open();
    await this.isLoaded();
  }

  async search(term: string): Promise<void> {
    const searchTerm = term.trim();
    if (!searchTerm) {
      await this.open();
      return;
    }
    if (searchTerm.length < 3) throw new Error("Shared Documents searches require at least three characters.");

    const oldTable = await this.page.locator("#sharedRecordsListContainer .mainTable").elementHandle();
    await this.searchInput.fill(searchTerm);
    await this.searchButton.click();
    if (oldTable) {
      await this.page.waitForFunction((table) => !table.isConnected, oldTable);
    }
    // Legacy blockUI.html uses data-test-id, not Playwright's data-testid.
    await this.page.locator('[data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
    await expect(this.page.locator("#searchModePanel #message")).toContainText(searchTerm);
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
