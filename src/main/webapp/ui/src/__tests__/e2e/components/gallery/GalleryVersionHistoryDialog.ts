import type { Locator, Page } from "@playwright/test";

export class GalleryVersionHistoryDialog {
  readonly root: Locator;
  readonly rows: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Version history:" }) });
    this.rows = this.root.getByRole("rowgroup").nth(1).getByRole("row");
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  versionLink(versionLabel: string): Locator {
    return this.root.getByRole("link", { name: versionLabel, exact: true });
  }

  nameCell(fileName: string): Locator {
    return this.root.getByRole("cell", { name: fileName, exact: true });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
