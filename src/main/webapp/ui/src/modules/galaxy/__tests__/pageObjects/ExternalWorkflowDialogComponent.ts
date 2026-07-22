import type { Locator, Page } from "@playwright/test";

export class ExternalWorkflowDialogComponent {
  readonly root: Locator;
  readonly grid: Locator;
  readonly closeButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog").filter({
      has: page.getByRole("heading", { name: "Galaxy Workflow Data" }),
    });
    this.grid = this.root.getByRole("grid");
    this.closeButton = this.root.getByRole("button", { name: "Close" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  columnHeader(name: string): Locator {
    return this.grid.getByRole("columnheader", { name, exact: true });
  }

  dataRow(fileName: string): Locator {
    return this.grid.getByRole("row").filter({
      has: this.page.getByRole("link", { name: fileName }),
    });
  }

  historyLink(fileName: string, historyName: string): Locator {
    return this.dataRow(fileName).getByRole("link", { name: historyName });
  }

  async close(): Promise<void> {
    await this.closeButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
