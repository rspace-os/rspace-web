import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class MyBookingsPage extends BasePage {
  readonly path = "/booking/my-bookings";

  readonly heading: Locator;

  constructor(page: Page) {
    super(page);
    this.heading = page.getByRole("heading", { level: 1, name: "My Bookings" });
  }

  async open(): Promise<void> {
    await this.page.goto(this.path);
    await this.heading.waitFor();
  }
}
