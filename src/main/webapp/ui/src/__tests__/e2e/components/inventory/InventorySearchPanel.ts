import { expect, type Locator, type Page } from "@playwright/test";
import { BasketsMenuComponent } from "./BasketsMenuComponent";
import { openDialog } from "./DialogHelpers";
import { InventoryBatchActionBar } from "./InventoryBatchActionBar";
import { SearchableResultsTable } from "./SearchableResultsTable";

export type InventoryFilterChip = "Type" | "Owner" | "Bench" | "Status" | "Barcode" | "Tags" | "Baskets";

export type InventoryViewMode = "List" | "Tree" | "Card" | "Grid";

export class InventorySearchPanel {
  private readonly root: Locator;
  private readonly searchInput: Locator;
  private readonly submitButton: Locator;
  private readonly resultsTable: Locator;
  readonly statusText: Locator;
  readonly batchActions: InventoryBatchActionBar;
  private readonly cardList: Locator;
  private readonly table: SearchableResultsTable;
  private readonly clearSearchButton: Locator;
  private readonly selectAllButton: Locator;
  private readonly previousPageButton: Locator;
  readonly nextPageButton: Locator;
  private readonly backButton: Locator;
  private readonly changeViewButton: Locator;
  private readonly hideRightPanelButton: Locator;
  private readonly showRightPanelButton: Locator;
  private readonly activeDetailsHeading: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("navigation", { name: "Search and Navigation" });
    this.searchInput = this.root.getByRole("searchbox", { name: "Search" });
    this.submitButton = this.root.getByRole("button", { name: "Search", exact: true }).first();
    this.resultsTable = this.root.getByRole("table", { name: "Search results" });
    this.statusText = this.root.getByRole("status", { name: "Search status" });
    this.batchActions = new InventoryBatchActionBar(page, this.resultsTable);
    this.cardList = this.root.getByRole("list", { name: "Inventory card list" });

    this.table = new SearchableResultsTable(this.root);
    this.clearSearchButton = this.root.getByRole("button", { name: "close" });
    this.selectAllButton = this.root.getByRole("button", { name: "Select all" });
    this.previousPageButton = this.root.getByRole("button", { name: "Go to previous page" });
    this.nextPageButton = this.root.getByRole("button", { name: "Go to next page" });
    this.backButton = page.getByRole("button", { name: "Back", exact: true });
    this.changeViewButton = this.root.getByRole("button", { name: "Change view" });
    this.hideRightPanelButton = this.root.getByLabel("Hide right panel", { exact: true });
    this.showRightPanelButton = this.root.getByLabel("Show right panel", { exact: true });
    this.activeDetailsHeading = page.getByRole("main").getByRole("heading", { level: 2 });
  }

  private async waitForSearchRequest(action: () => Promise<void>, expectedQuery?: string): Promise<void> {
    await Promise.all([
      this.page.waitForResponse((res) => {
        const url = new URL(res.url());
        if (!url.pathname.endsWith("/api/inventory/v1/search")) return false;
        if (res.request().method() !== "GET") return false;
        if (expectedQuery === undefined) return true;
        return url.searchParams.get("query") === expectedQuery;
      }),
      action(),
    ]);
  }

  async openBasketsMenu(): Promise<BasketsMenuComponent> {
    return openDialog(() => this.filterChip("Baskets").click(), new BasketsMenuComponent(this.page));
  }

  async ensureVisible(): Promise<void> {
    await expect(async () => {
      if (await this.backButton.isVisible().catch(() => false)) {
        await this.backButton.click();
      }
      await this.searchInput.waitFor({ state: "visible", timeout: 2_000 });
    }).toPass({ timeout: 10_000 });
  }

  async search(query: string): Promise<void> {
    await this.ensureVisible();
    await this.searchInput.fill(query);
    await this.waitForSearchRequest(() => this.submitButton.click(), query);
  }

  async clearSearch(): Promise<void> {
    await this.waitForSearchRequest(() => this.clearSearchButton.click());
  }

  filterChip(name: InventoryFilterChip): Locator {
    return this.root.getByRole("button", { name, exact: true });
  }

  async selectAll(): Promise<void> {
    await this.selectAllButton.click();
  }

  row(name: string, options: { exact?: boolean } = {}): Locator {
    return this.table.row(name, options);
  }

  async open(name: string): Promise<void> {
    await this.table.open(name);
    await this.activeDetailsHeading.filter({ hasText: name }).waitFor({ state: "visible" });
  }

  async openFirstResult(): Promise<void> {
    const row = this.resultsTable.locator("tbody").getByRole("row").first();
    await row.dispatchEvent("click");
    await this.activeDetailsHeading.waitFor({ state: "visible" });
  }

  async rowCount(): Promise<number> {
    return await this.resultsTable.locator("tbody").getByRole("row").count();
  }

  async changeView(view: InventoryViewMode): Promise<void> {
    await this.changeViewButton.click();

    await this.page.getByRole("menuitem", { name: `${view} View`, exact: true }).click();
    const target = view === "List" ? this.resultsTable : view === "Tree" ? this.tree() : this.cardList;
    await target.waitFor({ state: "visible" });
  }

  async hideRightPanel(): Promise<void> {
    if (await this.hideRightPanelButton.isVisible().catch(() => false)) {
      await this.hideRightPanelButton.dispatchEvent("click");
      await this.showRightPanelButton.waitFor({ state: "visible" });
    }
  }

  async showRightPanel(): Promise<void> {
    if (await this.showRightPanelButton.isVisible().catch(() => false)) {
      await this.showRightPanelButton.click();
      await this.hideRightPanelButton.waitFor({ state: "visible" });
    }
  }

  async hasRightPanelToggle(): Promise<boolean> {
    return (
      (await this.hideRightPanelButton.isVisible().catch(() => false)) ||
      (await this.showRightPanelButton.isVisible().catch(() => false))
    );
  }

  card(index: number): Locator {
    return this.cardList.getByRole("listitem").nth(index);
  }

  tree(): Locator {
    return this.root.getByRole("tree").first();
  }

  async columnOptionsCount(): Promise<number> {
    return await this.root.getByLabel("Column options").count();
  }

  async selectItem(name: string): Promise<void> {
    await this.table.check(name);
  }

  async resultCount(): Promise<string> {
    return this.statusText.innerText();
  }

  async goToNextPage(): Promise<void> {
    await this.waitForSearchRequest(() => this.nextPageButton.click());
  }

  async goToPreviousPage(): Promise<void> {
    await this.waitForSearchRequest(() => this.previousPageButton.click());
  }
}
