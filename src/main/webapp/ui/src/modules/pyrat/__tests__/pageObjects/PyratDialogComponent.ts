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
    const errorAlert = this.root.getByRole("alert");
    await Promise.race([
      this.resultsTable.waitFor({ state: "visible" }),
      errorAlert.waitFor({ state: "visible" }).then(async () => {
        const message = await errorAlert.textContent();
        throw new Error(`PyRAT dialog showed an error instead of loading results: ${message}`);
      }),
    ]);
  }

  async selectAnimal(eartagOrId: string): Promise<void> {
    await this.resultsTable.getByRole("cell", { name: eartagOrId, exact: true }).click();
  }

  async selectFirstAnimal(): Promise<string> {
    const dataRowGroup = this.resultsTable.getByRole("rowgroup").nth(1);
    const idCell = dataRowGroup.getByRole("cell").nth(1);
    const eartagOrId = (await idCell.textContent())?.trim();
    if (!eartagOrId) throw new Error("The first PyRAT animal's ID cell has no text.");
    await idCell.click();
    return eartagOrId;
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
