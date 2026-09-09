import type { Locator, Page } from "@playwright/test";

export class WitnessDialogComponent {
  readonly dialog: Locator;

  constructor(page: Page) {
    this.dialog = page
      .getByRole("dialog", { name: "Witnessing Document" })
      .or(page.getByRole("dialog", { name: "Witnessing Entry" }));
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  async confirm(password: string): Promise<void> {
    await this.dialog.getByRole("button", { name: "Witness", exact: true }).click();
    await this.dialog.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.dialog.getByRole("button", { name: "Proceed" }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }
}
