import type { Locator, Page } from "@playwright/test";

export class ChangePasswordDialogComponent {
  readonly root: Locator;
  readonly currentPasswordField: Locator;
  readonly newPasswordField: Locator;
  readonly confirmPasswordField: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Change Password" });
    this.currentPasswordField = this.root.getByRole("textbox", { name: "Enter current password" });
    this.newPasswordField = this.root.getByRole("textbox", { name: "Enter new password" });
    this.confirmPasswordField = this.root.getByRole("textbox", { name: "Confirm new password" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async save(currentPassword: string, newPassword: string): Promise<void> {
    await this.currentPasswordField.fill(currentPassword);
    await this.newPasswordField.fill(newPassword);
    await this.confirmPasswordField.fill(newPassword);
    await this.root.getByRole("button", { name: "Save", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  successMessage(textSubstring: string): Locator {
    return this.page.getByRole("alert").filter({ hasText: textSubstring });
  }
}
