import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class RequestPasswordResetPage extends BasePage {
  readonly path = "/public/requestPasswordReset";

  readonly emailInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    // No <label> for this field in the JSP (see requestPasswordReset.jsp) — the
    // placeholder is the only user-facing text, so it's the best available locator.
    this.emailInput = page.getByPlaceholder("Your Email Address");
    this.submitButton = page.locator('form[action="/signup/passwordResetRequest"]').getByRole("button", {
      name: "Submit",
    });
  }

  async requestReset(email: string): Promise<void> {
    await this.emailInput.fill(email);
    await this.submitButton.click();
    await this.page.waitForURL((url) => url.pathname === "/signup/passwordResetRequest");
  }
}
