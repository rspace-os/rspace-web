import type { FrameLocator, Locator, Page } from "@playwright/test";

export class OmeroDialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;
  readonly resultsTable: Locator;
  readonly insertButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Insert from Omero" });
    this.frame = this.root.frameLocator("iframe");
    this.resultsTable = this.frame.getByRole("table", { name: "item search results" });
    this.insertButton = this.root.getByRole("button", { name: "Insert", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.resultsTable.waitFor({ state: "visible" });
  }

  async selectFirstItem(): Promise<void> {
    await this.resultsTable.locator('input[type="checkbox"][aria-labelledby]').first().click();
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
