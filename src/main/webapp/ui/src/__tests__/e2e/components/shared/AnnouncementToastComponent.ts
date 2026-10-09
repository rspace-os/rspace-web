import type { Locator, Page } from "@playwright/test";

export class AnnouncementToastComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page
      .getByRole("paragraph")
      .filter({ hasText: "This is an important announcement from your RSpace administrator" });
  }

  withText(text: string): Locator {
    return this.root.filter({ hasText: text });
  }
}
