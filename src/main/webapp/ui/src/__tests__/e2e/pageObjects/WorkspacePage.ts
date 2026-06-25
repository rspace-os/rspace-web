import type { Locator, Page } from "@playwright/test";
import { BasePage } from "./BasePage";

export class WorkspacePage extends BasePage {
  readonly path = "/workspace";
  readonly createButton: Locator;

  constructor(page: Page) {
    super(page);
    this.createButton = page.locator('[data-test-id="create-btn"]');
  }

  async isLoaded(): Promise<boolean> {
    try {
      await this.createButton.waitFor({ state: "visible", timeout: 10_000 });
      return true;
    } catch {
      return false;
    }
  }
}
