import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";
import { ToolbarCreateMenu } from "@/__tests__/e2e/components/shared/ToolbarCreateMenu";

export class NotebookViewToolbar {
  readonly createMenu: ToolbarCreateMenu;
  readonly actions: ToolbarCommonActions;
  readonly editButton: Locator;
  readonly signButton: Locator;
  readonly witnessButton: Locator;

  constructor(page: Page) {
    this.createMenu = new ToolbarCreateMenu(page);
    this.actions = new ToolbarCommonActions(page);
    this.editButton = page.getByRole("button", { name: "Edit", exact: true });
    this.signButton = page.getByRole("button", { name: "Sign", exact: true });
    this.witnessButton = page.getByRole("button", { name: "Witness", exact: true });
  }
}
