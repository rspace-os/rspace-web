import { expect, type Locator, type Page } from "@playwright/test";

export type ItemsPerPage = 10 | 15 | 30 | 50;

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
    // Bootstrap exposes the current page only through its active class.
    return this.root.locator("li.active");
  }

  async getCurrentPage(): Promise<number> {
    const text = await this.activePageItem.innerText();
    return Number(text.trim());
  }

  private pageLink(n: number): Locator {
    return this.root.getByRole("link", { name: String(n), exact: true });
  }

  async goToPage(n: number): Promise<void> {
    await this.pageLink(n).click();
    await this.waitForPage(n);
  }

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
    await expect(this.activePageItem).toHaveText(String(n));
  }

  async setItemsPerPage(n: ItemsPerPage): Promise<void> {
    await this.itemsPerPageSelect.selectOption(String(n));
    await expect(this.itemsPerPageSelect).toHaveValue(String(n));
    await this.applyItemsPerPageButton.click();
    await this.waitForPage(1);
    await this.itemsPerPageSelect.page().locator('[data-test-id="blockUIImg"]').waitFor({ state: "hidden" });
  }
}
