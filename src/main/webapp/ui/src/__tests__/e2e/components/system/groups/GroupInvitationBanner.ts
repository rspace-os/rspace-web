import type { Locator, Page } from "@playwright/test";

export class GroupInvitationBanner {
  private readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  get acceptButton(): Locator {
    return this.page.locator("#acceptLabGrpRequest");
  }

  async accept(): Promise<void> {
    await this.acceptButton.click();
    await this.acceptButton.waitFor({ state: "hidden" });
  }
}
