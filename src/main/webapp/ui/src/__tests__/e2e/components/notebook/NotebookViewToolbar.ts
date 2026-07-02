import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";
import { ToolbarCreateMenu } from "@/__tests__/e2e/components/shared/ToolbarCreateMenu";

/**
 * Notebook view (`/notebookEditor/`) toolbar.
 */
export class NotebookViewToolbar {
  readonly createMenu: ToolbarCreateMenu;
  readonly actions: ToolbarCommonActions;
  readonly editButton: Locator;
  readonly signButton: Locator;
  readonly witnessButton: Locator;

  constructor(page: Page) {
    this.createMenu = new ToolbarCreateMenu(page);
    this.actions = new ToolbarCommonActions(page);
    this.editButton = page.getByTestId("notebooktoolbar-edit");
    this.signButton = page.getByTestId("notebooktoolbar-sign");
    this.witnessButton = page.getByTestId("notebooktoolbar-witness");
  }
}
