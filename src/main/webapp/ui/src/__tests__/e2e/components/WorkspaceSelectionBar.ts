import type { Locator, Page } from "@playwright/test";

/**
 * Selection action bar. Appears below the toolbar once ≥1 row checkbox is
 * checked; detaches when all are deselected. Legacy jQuery `<ul><li>` with no
 * `data-test-id` and no explicit ARIA roles (browsers still expose the
 * implicit list/listitem semantics of `<ul>`/`<li>`).
 *
 * `Add to Favorites`/`Remove from Favorites` is a single toggling item, not
 * two. `Publish` only renders for publishable record types. Scope by the
 * "Duplicate" item — it's the one item present whenever the bar is shown —
 * to avoid the page's other `<ul>`s (pagination, nav links).
 */
export class WorkspaceSelectionBar {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("list").filter({
      has: page.getByRole("listitem").filter({ hasText: "Duplicate" }),
    });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private item(name: string): Locator {
    return this.root.getByRole("listitem").filter({ hasText: name });
  }

  async clickAction(
    action: "Duplicate" | "Move" | "Rename" | "Delete" | "Export" | "Revisions" | "Publish",
  ): Promise<void> {
    await this.item(action).click();
  }

  async toggleFavorite(): Promise<void> {
    const current = this.item("Add to Favorites");
    const toggled = (await current.count()) > 0 ? current : this.item("Remove from Favorites");
    await toggled.click();
  }

  async exportAsCsv(): Promise<void> {
    await this.item("CSV").getByRole("link", { name: "CSV" }).click();
  }

  async share(): Promise<void> {
    await this.item("Share").getByRole("link", { name: "Share" }).click();
  }

  async addRemoveTags(): Promise<void> {
    await this.item("Add/Remove Tags").getByRole("link", { name: "Add/Remove Tags" }).click();
  }
}
