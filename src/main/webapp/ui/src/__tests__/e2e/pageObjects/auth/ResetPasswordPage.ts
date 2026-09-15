import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class ResetPasswordPage extends BasePage {
  readonly path = "/signup/passwordResetReply";

  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    this.passwordInput = page.getByLabel("New password", { exact: true });
    this.confirmPasswordInput = page.getByLabel("Confirm password", { exact: true });
    this.submitButton = page.locator('form[action="/signup/passwordResetReply"]').getByRole("button", {
      name: "Reset",
    });
  }

  async submitNewPassword(newPassword: string): Promise<void> {
    await this.passwordInput.fill(newPassword);
    await this.confirmPasswordInput.fill(newPassword);
    await this.submitButton.click();
  }
}
