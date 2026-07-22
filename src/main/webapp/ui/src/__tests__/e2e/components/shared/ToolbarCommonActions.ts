import type { Locator, Page } from "@playwright/test";

export class ToolbarCommonActions {
  readonly closeLink: Locator;
  readonly deleteButton: Locator;
  readonly exportButton: Locator;
  readonly printButton: Locator;

  constructor(page: Page) {
    this.closeLink = page.getByRole("link", { name: /^(Close|Back)$/ });
    this.deleteButton = page.getByRole("button", { name: "Delete" });
    this.exportButton = page.getByRole("button", { name: "Export" });
    this.printButton = page.getByRole("button", { name: "Print" });
  }
}
