import type { Locator, Page } from "@playwright/test";

export type FilestoreLinkDetail = "Original path:" | "URL:" | "Bucket:";

export class FilestoreLinkDetailsDialog {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Filestore link details" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  detail(label: FilestoreLinkDetail): Locator {
    return this.root
      .getByRole("row")
      .filter({ has: this.page.getByRole("cell", { name: label, exact: true }) })
      .getByRole("cell")
      .nth(1);
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
