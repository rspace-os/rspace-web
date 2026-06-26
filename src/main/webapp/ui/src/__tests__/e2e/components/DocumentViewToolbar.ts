import type { Locator, Page } from "@playwright/test";

/**
 * View-mode toolbar. Edit-mode actions (Save, Cancel) are in DocumentToolbar.
 *
 * Note: `signButton` uses `structured-sign` which only appears in the DOM
 * when the document is in a signable state — it may not be attached for all docs.
 */
export class DocumentViewToolbar {
  readonly saveAsTemplateButton: Locator;
  readonly deleteButton: Locator;
  readonly signButton: Locator;
  readonly exportButton: Locator;
  readonly closeLink: Locator;

  constructor(page: Page) {
    this.saveAsTemplateButton = page.getByTestId("notebooktoolbar-saveAsTemplateBtn");
    this.deleteButton = page.getByTestId("structured-delete");
    this.signButton = page.getByTestId("structured-sign");
    this.exportButton = page.getByTestId("notebooktoolbar-export");
    this.closeLink = page.getByTestId("structured-document-back");
  }
}
