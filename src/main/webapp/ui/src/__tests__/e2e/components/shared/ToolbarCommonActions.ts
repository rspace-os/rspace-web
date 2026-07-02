import type { Locator, Page } from "@playwright/test";

/**
 * Delete/Export/Print/Close-or-Back actions shared across the structured-
 * document view toolbar, the structured-document edit toolbar, and the
 * notebook view toolbar.
 *
 */
export class ToolbarCommonActions {
  readonly closeLink: Locator;
  readonly deleteButton: Locator;
  readonly exportButton: Locator;
  readonly printButton: Locator;

  constructor(page: Page) {
    this.closeLink = page.getByTestId("structured-document-back");
    this.deleteButton = page.getByRole("button", { name: "Delete" });
    this.exportButton = page.getByRole("button", { name: "Export" });
    this.printButton = page.getByRole("button", { name: "Print" });
  }
}
