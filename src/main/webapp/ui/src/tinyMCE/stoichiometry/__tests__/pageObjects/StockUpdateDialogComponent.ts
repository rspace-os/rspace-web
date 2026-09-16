import type { Locator, Page } from "@playwright/test";

/** The "Update Inventory Stock" dialog opened from the Reaction Table toolbar's "Update Inventory Stock" button. */
export class StockUpdateDialogComponent {
  readonly root: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Update Inventory Stock", exact: true });
    this.saveButton = this.root.getByRole("button", { name: "Save", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  checkbox(moleculeName: string): Locator {
    return this.root.getByRole("checkbox", { name: moleculeName });
  }

  /** The table has no ARIA grid semantics; data-molecule-name/data-column are stable, component-owned attributes (mirrors the reaction table's data-field convention). */
  private row(moleculeName: string): Locator {
    return this.root.locator(`[data-row-type="molecule"][data-molecule-name="${moleculeName}"]`);
  }

  async remainingText(moleculeName: string): Promise<string> {
    return (await this.row(moleculeName).locator('[data-column="Remaining"]').innerText()).trim();
  }

  async isStockAlreadyDeducted(moleculeName: string): Promise<boolean> {
    return (await this.row(moleculeName).getByText("Stock deducted").count()) > 0;
  }

  async selectMolecule(moleculeName: string): Promise<void> {
    await this.checkbox(moleculeName).check();
  }

  async save(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse(
        (res) =>
          res.request().method() === "POST" && new URL(res.url()).pathname.endsWith("/stoichiometry/link/deductStock"),
      ),
      this.saveButton.click(),
    ]);
  }

  async errorMessage(): Promise<string> {
    return (await this.root.getByRole("alert").filter({ hasText: "Insufficient stock" }).innerText()).trim();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
