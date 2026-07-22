import type { Locator, Page } from "@playwright/test";
import { ToolbarCommonActions } from "@/__tests__/e2e/components/shared/ToolbarCommonActions";

export class DocumentViewToolbar {
  readonly saveAsTemplateButton: Locator;
  readonly signButton: Locator;
  readonly actions: ToolbarCommonActions;

  constructor(page: Page) {
    this.saveAsTemplateButton = page.getByRole("button", { name: "Save as Template" });
    this.signButton = page.getByRole("button", { name: "Sign", exact: true });
    this.actions = new ToolbarCommonActions(page);
  }
}
