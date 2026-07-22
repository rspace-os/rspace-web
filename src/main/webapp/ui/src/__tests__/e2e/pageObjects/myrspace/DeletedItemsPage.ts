import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class DeletedItemsPage extends BasePage {
  readonly path = "/workspace/trash/list";

  readonly searchInput: Locator;
  readonly searchButton: Locator;

  constructor(page: Page) {
    super(page);
    this.searchInput = page.getByRole("textbox", { name: "Search by name" });
    this.searchButton = page.getByRole("button", { name: "Search", exact: true });
  }

  async isLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Deleted Items" }).waitFor({ state: "visible" });
  }

  async search(term: string): Promise<void> {
    await this.searchInput.fill(term);
    await Promise.all([
      this.page.waitForResponse((res) => new URL(res.url()).pathname === "/workspace/trash/ajax/list"),
      this.searchButton.click(),
    ]);
  }

  row(name: string): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByRole("cell", { name, exact: true }) });
  }

  async viewDocument(name: string): Promise<void> {
    await Promise.all([
      this.page.waitForURL("**/audit/view**"),
      this.row(name).getByRole("link", { name: "View" }).click(),
    ]);
  }

  async restore(name: string): Promise<void> {
    await Promise.all([
      this.page.waitForURL((url) => !url.pathname.includes("/workspace/trash/list")),
      this.row(name).getByRole("link", { name: "Restore" }).click(),
    ]);
  }
}
