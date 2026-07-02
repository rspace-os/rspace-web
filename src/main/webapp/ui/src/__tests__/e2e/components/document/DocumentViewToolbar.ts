import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";

/**
 * View-mode toolbar. Edit-mode actions (Save, Cancel) are in `DocumentToolbar`.
 *
 */
export class DocumentViewToolbar {
  readonly saveAsTemplateButton: Locator;
  readonly signButton: Locator;
  readonly actions: ToolbarCommonActions;

  constructor(page: Page) {
    this.saveAsTemplateButton = page.getByTestId("notebooktoolbar-saveAsTemplateBtn");
    this.signButton = page.getByTestId("structured-sign");
    this.actions = new ToolbarCommonActions(page);
  }
}
