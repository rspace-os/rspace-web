import type { Locator, Page } from "@playwright/test";

export type MoveDialogSortField = "name" | "creationdate" | "modificationdate";
export type MoveDialogSortOrder = "ASC" | "DESC";

const ORDER_BY_LABEL: Record<MoveDialogSortField, string> = {
  name: "Name",
  creationdate: "Creation Date",
  modificationdate: "Last Modified",
};

const SORT_ORDER_LABEL: Record<MoveDialogSortOrder, string> = {
  ASC: "Ascending",
  DESC: "Descending",
};

export class MoveDialog {
  readonly root: Locator;
  readonly orderBy: Locator;
  readonly sortOrder: Locator;
  readonly tree: Locator;
  readonly path: Locator;
  readonly cancelButton: Locator;
  readonly moveButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Select target folder" });
    this.orderBy = this.root.getByRole("combobox", { name: "Order by" });
    this.sortOrder = this.root.getByRole("combobox", { name: "Sort order" });
    this.tree = this.root.locator("#movefolder-tree");
    this.path = this.root.locator("#folder-move-path");
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.moveButton = this.root.getByRole("button", { name: "Move" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  folder(name: string): Locator {
    return this.tree.getByRole("link", { name, exact: true });
  }

  async clickFolder(name: string): Promise<void> {
    const target = this.path.filter({ hasText: name });
    await this.folder(name).click();
    await target.waitFor({ state: "visible" });
  }

  async setOrdering(field: MoveDialogSortField, order: MoveDialogSortOrder): Promise<void> {
    await this.orderBy.selectOption({ label: ORDER_BY_LABEL[field] });
    await this.sortOrder.selectOption({ label: SORT_ORDER_LABEL[order] });
  }

  async visibleItemNames(): Promise<string[]> {
    return this.tree.getByRole("list").last().getByRole("link").allTextContents();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "hidden" });
  }

  async clickMove(): Promise<void> {
    const movePromise = this.page.waitForResponse((res) => res.url().includes("/workspace/ajax/move"));
    await this.moveButton.click();
    await movePromise;
    await this.root.waitFor({ state: "hidden" });
  }
}
