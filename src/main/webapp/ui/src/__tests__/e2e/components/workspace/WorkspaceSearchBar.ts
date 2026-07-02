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

const FILTER_TEST_ID: Record<SearchField, string> = {
  All: "s-search-filter-global",
  Text: "s-search-filter-fullText",
  "Tag(s)": "s-search-filter-tag",
  Name: "s-search-filter-name",
  Form: "s-search-filter-form",
  Template: "s-search-filter-template",
  "Creation date": "s-search-filter-created",
  "Last modified": "s-search-filter-lastModified",
  "Owner(s)": "s-search-filter-owner",
  Attachment: "s-search-filter-attachment",
  Chemical: "s-search-filter-chemical",
};

/**
 * Advanced search's own type set (`SEARCH_TYPES` in `AdvancedSearch.tsx`) —
 * distinct from simple search's: no "All"/"Chemical", but has
 * "Within records". Don't reuse `SearchField`/`FILTER_TEST_ID` for advanced
 * search rows.
 */
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

const ADVANCED_OPTION_KEY: Record<AdvancedSearchField, string> = {
  Text: "fullText",
  "Tag(s)": "tag",
  Name: "name",
  Form: "form",
  Template: "template",
  "Creation date": "created",
  "Last modified": "lastModified",
  "Owner(s)": "owner",
  Attachment: "attachment",
  "Within records": "records",
};

/**
 * Simple search and advanced search Only rendered in list/folder view — tree view replaces
 * this toolbar section with sort controls (see `WorkspaceTree`).
 *
 * `s-search-input-normal` is a wrapper `div`, not the input itself; the
 * actual `<input>` has `aria-label="Search"`
 *
 * Advanced search rows use index-based test IDs generated fresh each time
 * the panel opens — `addRow()` always resets first so indices are
 * predictable.
 *
 * After any search (simple or advanced) submits, a "Showing results of your
 * search." banner with a "Clear search" button appears above the table
 *
 */
export class WorkspaceSearchBar {
  readonly searchInput: Locator;
  readonly submitButton: Locator;
  readonly filterButton: Locator;
  readonly advancedToggle: Locator;
  readonly clearSearchButton: Locator;

  constructor(private readonly page: Page) {
    this.searchInput = page.getByRole("textbox", { name: "Search" });
    this.submitButton = page.getByTestId("s-search-submit");
    this.filterButton = page.getByTestId("s-search-filter");
    this.advancedToggle = page.getByTestId("toggle-advanced");
    this.clearSearchButton = page.getByRole("button", { name: "Clear search" });
  }

  async isSearchActive(): Promise<boolean> {
    return (await this.clearSearchButton.count()) > 0;
  }

  async setFilter(field: SearchField): Promise<void> {
    await this.filterButton.click();
    await this.page.getByTestId(FILTER_TEST_ID[field]).click();
  }

  async search(term: string): Promise<void> {
    await this.searchInput.fill(term);
    await this.submitButton.click();
  }

  /** Clears results from either search mode via the "Clear search" banner button. */
  async clearSearch(): Promise<void> {
    if (await this.isSearchActive()) {
      await this.clearSearchButton.click();
    }
  }

  async isAdvancedOpen(): Promise<boolean> {
    return (await this.page.getByTestId("a-search-submit").count()) > 0;
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

  private rowTypeCombobox(idx: number): Locator {
    return this.page.getByTestId(`a-search-type-${idx}`);
  }

  /** Adds a new advanced-search row and returns its index. */
  async addRow(): Promise<number> {
    const before = await this.page.getByTestId(/^a-search-type-/).count();
    await this.page.getByTestId("a-search-query-add").click();
    return before;
  }

  async setRowType(idx: number, type: AdvancedSearchField): Promise<void> {
    await this.rowTypeCombobox(idx).click();
    await this.page.getByTestId(`a-search-option-${ADVANCED_OPTION_KEY[type]}`).click();
  }

  /**
   * Sets a row's value — only for the plain-text types (`Text`, `Name`,
   * `Form`, `Template`, `Attachment`), where `a-search-input-${idx}` wraps an
   * `<input>` accessibly named "Search term". `Owner(s)`/`Tag(s)` render an
   * autocomplete under the same test ID (needs selecting a suggestion, not
   * `.fill()`); `Creation date`/`Last modified` render two `DateField`s
   * instead (see `setRowDateRange`); `Within records` renders a `FilePicker`
   * with no test ID. None of those four are implemented here yet — verify
   * live before adding them.
   */
  async setRowValue(idx: number, value: string): Promise<void> {
    await this.page.getByTestId(`a-search-input-${idx}`).getByRole("textbox").fill(value);
  }

  /**
   * For rows set to `Creation date` or `Last modified` — dates as `YYYY-MM-DD`.
   */
  async setRowDateRange(idx: number, from?: string, to?: string): Promise<void> {
    if (from !== undefined) await this.page.getByTestId(`a-search-input-${idx}-from`).fill(from);
    if (to !== undefined) await this.page.getByTestId(`a-search-input-${idx}-to`).fill(to);
  }

  async removeRow(idx: number): Promise<void> {
    await this.page.getByTestId(`a-search-rmv-${idx}`).click();
  }

  async submitAdvanced(): Promise<void> {
    await this.page.getByTestId("a-search-submit").click();
  }

  async resetAdvanced(): Promise<void> {
    await this.page.getByTestId("a-search-reset").click();
  }
}
