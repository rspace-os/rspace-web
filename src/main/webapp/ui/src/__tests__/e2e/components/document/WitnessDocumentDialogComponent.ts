import type { Locator, Page } from "@playwright/test";

export class WitnessDocumentDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page
      .getByRole("dialog", { name: "Witnessing Document" })
      .or(page.getByRole("dialog", { name: "Witnessing Entry" }));
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async witnessWithPassword(password: string): Promise<void> {
    await this.root.getByRole("button", { name: "Witness", exact: true }).click();
    await this.root.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.root.getByRole("button", { name: "Proceed", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  /** Declines with a reason instead of confirming; the reason field is a legacy JSP input with no accessible name. */
  async declineWithPassword(password: string, reason: string): Promise<void> {
    await this.root.getByRole("radio", { name: "I decline to add a witness statement" }).click();
    await this.root.locator("#declineMsgInput").fill(reason);
    await this.root.getByRole("button", { name: "Witness", exact: true }).click();
    await this.root.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.root.getByRole("button", { name: "Proceed", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
