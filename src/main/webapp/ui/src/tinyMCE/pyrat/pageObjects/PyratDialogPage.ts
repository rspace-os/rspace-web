import { type Locator, page } from "vitest/browser";

/**
 * Page object for the PyRAT dialog as mounted by PyratDialogStory.
 * Locator getters + user actions only; assertions live in the spec.
 */
export class PyratDialogPage {
  /**
   * One locator per body row, matched by its eartag ID cell (e.g. "A0007").
   * We key off the ID value rather than the row element because the results
   * table gives each body row role="checkbox" (for row selection), so
   * getByRole("row") only ever matches the header row.
   */
  dataRows(): Locator {
    return page.getByText(/^A\d{4}$/);
  }

  dataRowCount(): number {
    return this.dataRows().elements().length;
  }

  get rowsPerPageSelect(): Locator {
    return page.getByRole("combobox", { name: "Rows per page:" });
  }

  get nextPageButton(): Locator {
    return page.getByRole("button", { name: "Go to next page" });
  }

  get showFilterOptionsButton(): Locator {
    return page.getByRole("button", { name: "Show filtering options" });
  }

  get animalTypeSelect(): Locator {
    return page.getByRole("combobox", { name: "Animal Type" });
  }

  get applyFilterButton(): Locator {
    return page.getByRole("button", { name: "Filter", exact: true });
  }

  /**
   * The sortable column header. Clicking the label text bubbles to the
   * enclosing TableSortLabel's onClick, which triggers the sort. We target the
   * text rather than a role because MUI's TableSortLabel exposes no stable
   * accessible-name handle.
   */
  columnHeaderLabel(label: string): Locator {
    return page.getByText(label, { exact: true });
  }

  async selectRowsPerPage(size: number): Promise<void> {
    await this.rowsPerPageSelect.click();
    await page.getByRole("option", { name: String(size), exact: true }).click();
  }

  async goToNextPage(): Promise<void> {
    await this.nextPageButton.click();
  }

  async sortByColumn(label: string): Promise<void> {
    await this.columnHeaderLabel(label).click();
  }

  async filterByAnimalType(animalType: "Animal" | "Pup"): Promise<void> {
    await this.showFilterOptionsButton.click();
    await this.animalTypeSelect.click();
    await page.getByRole("option", { name: animalType, exact: true }).click();
    await this.applyFilterButton.click();
  }
}
