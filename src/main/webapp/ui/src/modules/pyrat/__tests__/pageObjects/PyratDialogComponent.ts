import type { Locator, Page } from "@playwright/test";

export class PyratDialogComponent {
  readonly root: Locator;
  readonly resultsTable: Locator;
  readonly insertButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Insert from PyRAT" });
    this.resultsTable = this.root.getByRole("table", { name: "animal search results" });
    this.insertButton = this.root.getByRole("button", { name: "Insert" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.resultsTable.waitFor({ state: "visible" });
  }

  async selectAnimal(eartagOrId: string): Promise<void> {
    await this.resultsTable.getByRole("cell", { name: eartagOrId, exact: true }).click();
  }

  async clickInsert(): Promise<void> {
    await this.insertButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
