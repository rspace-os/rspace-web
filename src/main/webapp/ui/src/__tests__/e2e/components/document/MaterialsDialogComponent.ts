import type { Locator, Page } from "@playwright/test";

export class MaterialsDialogComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "List of Materials (Inventory)" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async addItems(): Promise<void> {
    await this.root.getByRole("button", { name: "Add items", exact: true }).click();
  }

  async filterPickerByType(type: string): Promise<void> {
    await this.root.getByRole("button", { name: "Type", exact: true }).click();
    await this.page.getByRole("menuitem", { name: type, exact: true }).click();
  }

  async searchPicker(query: string): Promise<void> {
    const searchInput = this.root.getByRole("searchbox", { name: "Search" });
    await searchInput.fill(query);
    await this.root.getByRole("button", { name: "Search", exact: true }).first().click();
  }

  async selectPickerResult(name: string): Promise<void> {
    await this.root.getByRole("row", { name }).getByRole("checkbox", { name: "Select " }).check();
  }

  async choosePickerSelection(): Promise<void> {
    await this.root.getByRole("button", { name: "Choose", exact: true }).click();
  }

  materialRow(name: string): Locator {
    return this.root.getByRole("row", { name });
  }

  async save(): Promise<void> {
    await this.root.getByRole("button", { name: "Save", exact: true }).click();
    await this.root.getByRole("button", { name: "Close", exact: true }).waitFor({ state: "visible" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close", exact: true }).click();
    await this.root.waitFor({ state: "detached" });
  }
}
