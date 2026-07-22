import type { Locator, Page } from "@playwright/test";

export class PublicNavComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    // The legacy public nav has no accessible name; this is its only stable root hook.
    this.root = page.locator("nav.rs-navbar");
  }

  async goToSignUp(): Promise<void> {
    await this.page.getByRole("link", { name: "Sign up" }).click();
  }

  async goToPublishedDocuments(): Promise<void> {
    await this.page.getByRole("link", { name: "Published documents" }).click();
  }
}
