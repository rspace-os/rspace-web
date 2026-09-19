import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class RequestUsernameReminderPage extends BasePage {
  readonly path = "/public/requestUsernameReminder";

  readonly emailInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    this.emailInput = page.getByPlaceholder("Your Email Address");
    this.submitButton = page.locator('form[action="/signup/usernameReminderRequest"]').getByRole("button", {
      name: "Submit",
    });
  }

  async requestReminder(email: string): Promise<void> {
    await this.emailInput.fill(email);
    await this.submitButton.click();
    await this.page.waitForURL((url) => url.pathname === "/signup/usernameReminderRequest");
  }
}
