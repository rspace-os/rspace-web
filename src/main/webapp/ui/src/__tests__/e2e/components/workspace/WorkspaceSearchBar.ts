import type { Locator, Page } from "@playwright/test";

export type SearchField =
  | "All"
  | "Text"
  | "Tag(s)"
  | "Name"
  | "Form"
  | "Template"
  | "Creation date"
  | "Last modified"
  | "Owner(s)"
  | "Attachment"
  | "Chemical";

export type AdvancedSearchField =
  | "Text"
  | "Tag(s)"
  | "Name"
  | "Form"
  | "Template"
  | "Creation date"
  | "Last modified"
  | "Owner(s)"
  | "Attachment"
  | "Within records";

export class WorkspaceSearchBar {
  readonly searchInput: Locator;
  readonly submitButton: Locator;
  readonly filterButton: Locator;
  readonly advancedToggle: Locator;
  readonly clearSearchButton: Locator;

  constructor(private readonly page: Page) {
    this.searchInput = page.getByRole("textbox", { name: "Search" });
    this.submitButton = page.getByRole("button", { name: "Search", exact: true }).first();
    this.filterButton = page.getByRole("button", { name: "Filters", exact: true });
    this.advancedToggle = page.getByRole("button", { name: "Advanced search", exact: true });
    this.clearSearchButton = page.getByRole("button", { name: "Clear search" });
  }

  async isSearchActive(): Promise<boolean> {
    return (await this.clearSearchButton.count()) > 0;
  }

  async setFilter(field: SearchField): Promise<void> {
    await this.filterButton.click();
    await this.page.getByRole("menuitem", { name: field, exact: true }).click();
  }

  async search(term: string): Promise<void> {
    await this.searchInput.fill(term);
    await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname.endsWith("/workspace/ajax/search")),
      this.submitButton.click(),
    ]);
    await this.page.locator('[data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
  }

  async searchByOwner(query: string): Promise<void> {
    await this.setFilter("Owner(s)");
    const combobox = this.page.getByRole("combobox", { name: "Select owner(s)" });
    await combobox.fill(query);
    await this.page.getByRole("option", { name: new RegExp(query) }).click();
    await this.submitButton.click();
  }

  async clearSearch(): Promise<void> {
    if (await this.isSearchActive()) {
      await this.clearSearchButton.click();
    }
  }

  async isAdvancedOpen(): Promise<boolean> {
    return (await this.page.getByRole("button", { name: "Reset", exact: true }).count()) > 0;
  }

  async openAdvanced(): Promise<void> {
    if (!(await this.isAdvancedOpen())) {
      await this.advancedToggle.click();
    }
  }

  async closeAdvanced(): Promise<void> {
    if (await this.isAdvancedOpen()) {
      await this.advancedToggle.click();
    }
  }

  private advancedRows(): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByRole("combobox") });
  }

  private advancedRow(idx: number): Locator {
    return this.advancedRows().nth(idx);
  }

  private rowTypeCombobox(idx: number): Locator {
    return this.advancedRow(idx).getByRole("combobox", { name: "Search type", exact: true });
  }

  async addRow(): Promise<number> {
    const before = await this.advancedRows().count();
    await this.page.getByRole("button", { name: "Add new condition", exact: true }).click();
    return before;
  }

  async setRowType(idx: number, type: AdvancedSearchField): Promise<void> {
    const option = this.page.getByRole("option", { name: type, exact: true });
    if ((await option.count()) === 0) {
      await this.rowTypeCombobox(idx).click();
    }
    await option.click();
  }

  async setRowValue(idx: number, value: string): Promise<void> {
    await this.advancedRow(idx).getByRole("textbox", { name: "Search term", exact: true }).fill(value);
  }

  private rowValueCombobox(idx: number): Locator {
    return this.advancedRow(idx).getByRole("combobox").last();
  }

  async setRowOwner(idx: number, query: string): Promise<void> {
    const combobox = this.rowValueCombobox(idx);
    await combobox.fill(query);
    await this.page.getByRole("option", { name: new RegExp(query) }).click();
  }

  async setRowTag(idx: number, tag: string): Promise<void> {
    const combobox = this.rowValueCombobox(idx);
    await combobox.fill(tag);
    await this.page.getByRole("option", { name: tag, exact: true }).click();
  }

  async setRowDateRange(idx: number, from?: string, to?: string): Promise<void> {
    if (from !== undefined) await this.advancedRow(idx).getByLabel("From", { exact: true }).fill(from);
    if (to !== undefined) await this.advancedRow(idx).getByLabel("To", { exact: true }).fill(to);
  }

  async removeRow(idx: number): Promise<void> {
    await this.advancedRow(idx).getByRole("button", { name: "Remove condition", exact: true }).click();
  }

  async setMatchMode(mode: "all" | "any"): Promise<void> {
    await this.page
      .getByRole("radio", { name: mode === "all" ? "Satisfy all conditions" : "Satisfy at least one condition" })
      .check();
  }

  async submitAdvanced(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname.endsWith("/workspace/ajax/search")),
      this.page.getByRole("button", { name: "Search", exact: true }).last().click(),
    ]);
    await this.page.locator('[data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
  }

  async resetAdvanced(): Promise<void> {
    await this.page.getByRole("button", { name: "Reset", exact: true }).click();
  }
}
