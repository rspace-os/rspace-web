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
    await this.rowTypeCombobox(idx).click();
    await this.page.getByRole("option", { name: type, exact: true }).click();
  }

  async setRowValue(idx: number, value: string): Promise<void> {
    await this.advancedRow(idx).getByRole("textbox", { name: "Search term", exact: true }).fill(value);
  }

  async setRowDateRange(idx: number, from?: string, to?: string): Promise<void> {
    if (from !== undefined) await this.advancedRow(idx).getByLabel("From", { exact: true }).fill(from);
    if (to !== undefined) await this.advancedRow(idx).getByLabel("To", { exact: true }).fill(to);
  }

  async removeRow(idx: number): Promise<void> {
    await this.advancedRow(idx).getByRole("button", { name: "Remove condition", exact: true }).click();
  }

  async submitAdvanced(): Promise<void> {
    await this.page.getByRole("button", { name: "Search", exact: true }).last().click();
  }

  async resetAdvanced(): Promise<void> {
    await this.page.getByRole("button", { name: "Reset", exact: true }).click();
  }
}
