import type { Locator, Page } from "@playwright/test";

/**
 * Record table — tree view. Legacy jQuery (Fancytree — nodes carry
 * `fancytree-*` classes and `data-id`/`data-globalid`/`data-name` attributes),
 * not React, but it renders correct `tree`/`treeitem`/`group` ARIA roles, so
 * role-based locators work the same as anywhere else. No Record Info link,
 * icon, or `data-test-id` exists on a treeitem — that action is list-view
 * only (see `WorkspacePage.openInfoFor`).
 *
 */
export class WorkspaceTree {
  readonly root: Locator;
  private readonly sortFieldCombobox: Locator;
  private readonly sortDirectionCombobox: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("tree");
    const comboboxes = page.getByRole("combobox");
    this.sortFieldCombobox = comboboxes.first();
    this.sortDirectionCombobox = comboboxes.last();
  }

  item(name: string): Locator {
    return this.root.getByRole("treeitem", { name });
  }

  async isExpanded(name: string): Promise<boolean> {
    return (await this.item(name).getAttribute("aria-expanded")) === "true";
  }

  async expand(name: string): Promise<void> {
    if (await this.isExpanded(name)) return;
    await this.item(name).getByRole("button").click();
  }

  async collapse(name: string): Promise<void> {
    if (!(await this.isExpanded(name))) return;
    await this.item(name).getByRole("button").click();
  }

  /** Clicks the name label (not the expand button) to open or navigate into an item. */
  async openItem(name: string): Promise<void> {
    await this.item(name).getByRole("generic").last().click();
  }

  children(parentName: string): Locator {
    return this.item(parentName).getByRole("group");
  }

  async sortBy(field: "name" | "creation-date" | "modification-date", direction: "asc" | "desc"): Promise<void> {
    await this.sortFieldCombobox.click();
    await this.page.getByTestId(`order-${field}`).click();
    await this.sortDirectionCombobox.click();
    await this.page.getByTestId(`sort-${direction}`).click();
  }
}
