import type { Locator, Page } from "@playwright/test";

/**
 * The public navigation bar (`<nav class="rs-navbar">`), present on all
 * unauthenticated pages (login, sign-up, published documents). Composed into
 * page objects rather than duplicating nav locators per page.
 */
export class PublicNavComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.locator("nav.rs-navbar");
  }

  async goToSignUp(): Promise<void> {
    await this.page.getByRole("link", { name: "Sign up" }).click();
  }

  async goToPublishedDocuments(): Promise<void> {
    await this.page.getByRole("link", { name: "Published documents" }).click();
  }
}
