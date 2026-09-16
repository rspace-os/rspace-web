import type { Locator, Page } from "@playwright/test";

/** The Inventory item picker opened from a Stoichiometry table row's "Add inventory link" action. */
export class PickInventoryItemDialogComponent {
  readonly root: Locator;

  constructor(page: Page, moleculeName: string) {
    this.root = page.getByRole("dialog", { name: `Pick inventory item for ${moleculeName}` });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  get searchInput(): Locator {
    return this.root.getByRole("searchbox", { name: "Search" });
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await this.root.getByRole("button", { name: "Search", exact: true }).first().click();
  }

  row(name: string): Locator {
    return this.root.getByRole("row", { name });
  }

  async select(name: string): Promise<void> {
    await this.row(name).getByRole("radio").check();
  }

  async isRowDisabled(name: string): Promise<boolean> {
    return this.row(name).getByRole("radio").isDisabled();
  }

  async choose(): Promise<void> {
    await this.root.getByRole("button", { name: "Choose", exact: true }).click();
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "detached" });
  }
}
