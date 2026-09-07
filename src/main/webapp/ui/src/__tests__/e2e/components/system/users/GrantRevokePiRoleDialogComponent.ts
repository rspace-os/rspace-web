import type { Locator, Page } from "@playwright/test";

export class GrantRevokePiRoleDialogComponent {
  readonly root: Locator;
  readonly passwordField: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog");
    this.passwordField = this.root.getByRole("textbox", { name: "Password" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async grant(password: string): Promise<void> {
    await this.passwordField.fill(password);
    await this.root.getByRole("button", { name: "Grant", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async revoke(password: string): Promise<void> {
    await this.passwordField.fill(password);
    await this.root.getByRole("button", { name: "Revoke", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async attemptRevoke(password: string): Promise<void> {
    await this.passwordField.fill(password);
    await this.root.getByRole("button", { name: "Revoke", exact: true }).click();
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
