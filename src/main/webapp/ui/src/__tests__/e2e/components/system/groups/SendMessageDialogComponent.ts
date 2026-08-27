import type { Locator, Page } from "@playwright/test";

export class SendMessageDialogComponent {
  readonly root: Locator;
  readonly toField: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Send a Message" });
    this.toField = this.root.getByRole("textbox", { name: "To" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async sendTo(username: string): Promise<void> {
    await this.toField.fill(username);
    await this.root.getByRole("listitem").filter({ hasText: username }).first().click();
    await this.root.getByRole("button", { name: "Send", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
