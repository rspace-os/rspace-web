import type { Locator, Page } from "@playwright/test";

export class SigningDialogComponent {
  readonly dialog: Locator;

  constructor(page: Page) {
    this.dialog = page.getByRole("dialog", { name: "Signing Document" });
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  async signWithoutWitness(password: string): Promise<void> {
    await this.dialog.getByRole("button", { name: "Sign", exact: true }).click();
    await this.dialog.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.dialog.getByRole("button", { name: "Proceed" }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }
}
