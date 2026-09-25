import type { Locator, Page } from "@playwright/test";

export class AppriseAlertComponent {
  readonly root: Locator;
  readonly message: Locator;
  readonly confirmButton: Locator;

  constructor(page: Page) {
    this.root = page.locator("div.apprise");
    this.message = this.root.locator(".apprise-content");
    this.confirmButton = page.locator("#apprise-btn-confirm");
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async confirm(): Promise<void> {
    await this.confirmButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
