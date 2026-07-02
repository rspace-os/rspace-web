import type { Locator, Page } from "@playwright/test";

export type ItemsPerPage = 10 | 15 | 30 | 50;

/**
 * Pagination + "Items per page" control below the Workspace record table
 * (list view only — not present in tree view).
 */
export class WorkspacePagination {
  readonly root: Locator;
  readonly itemsPerPageSelect: Locator;
  private readonly applyItemsPerPageButton: Locator;

  constructor(page: Page) {
    this.root = page.locator("ul.pagination");
    this.itemsPerPageSelect = page.getByRole("combobox", { name: "Items per page:" });
    this.applyItemsPerPageButton = page.locator("#applyNumberRecords");
  }

  private get activePageItem(): Locator {
    return this.root.locator("li.active");
  }

  async getCurrentPage(): Promise<number> {
    const text = await this.activePageItem.innerText();
    return Number(text.trim());
  }

  private pageLink(n: number): Locator {
    return this.root.getByRole("link", { name: String(n), exact: true });
  }

  /**
   * Only safe for a page number currently rendered in the pagination bar
   * (the current page ± 2, or the first/last 2 pages of the whole result
   * set — see class doc comment on truncation). For stepping through
   * unknown-length results, use `goToNextPage()` instead.
   */
  async goToPage(n: number): Promise<void> {
    await this.pageLink(n).click();
    await this.waitForPage(n);
  }

  /**
   * Advances one page if a next page exists. Returns `false` (does nothing)
   * if already on the last page — safe to call in a loop without checking
   * page count up front.
   */
  async goToNextPage(): Promise<boolean> {
    const current = await this.getCurrentPage();
    const next = this.pageLink(current + 1);
    if ((await next.count()) === 0) {
      return false;
    }
    await next.click();
    await this.waitForPage(current + 1);
    return true;
  }

  private async waitForPage(n: number): Promise<void> {
    await this.activePageItem.filter({ hasText: new RegExp(`^${n}$`) }).waitFor({ state: "visible" });
  }

  /** Selects the value AND clicks the Apply button that changing the select alone doesn't trigger. */
  async setItemsPerPage(n: ItemsPerPage): Promise<void> {
    await this.itemsPerPageSelect.selectOption(String(n));
    await this.applyItemsPerPageButton.click();
    await this.waitForPage(1);
  }
}
