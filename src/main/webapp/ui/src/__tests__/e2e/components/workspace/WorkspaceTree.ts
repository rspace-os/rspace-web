import type { Locator, Page } from "@playwright/test";

export class WorkspaceTree {
  readonly root: Locator;
  private readonly sortFieldCombobox: Locator;
  private readonly sortDirectionCombobox: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("tree");
    this.sortFieldCombobox = page.getByRole("combobox", { name: "Sort field", exact: true });
    this.sortDirectionCombobox = page.getByRole("combobox", { name: "Sort direction", exact: true });
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

  async openItem(name: string): Promise<void> {
    await this.item(name).getByRole("generic").last().click();
  }

  children(parentName: string): Locator {
    return this.item(parentName).getByRole("group");
  }

  async sortBy(field: "name" | "creation-date" | "modification-date", direction: "asc" | "desc"): Promise<void> {
    const fieldName = {
      name: "Name",
      "creation-date": "Creation Date",
      "modification-date": "Last Modified",
    }[field];
    await this.sortFieldCombobox.click();
    await this.page.getByRole("option", { name: fieldName, exact: true }).click();
    await this.sortDirectionCombobox.click();
    await this.page
      .getByRole("option", { name: direction === "asc" ? "Ascending" : "Descending", exact: true })
      .click();
  }
}
