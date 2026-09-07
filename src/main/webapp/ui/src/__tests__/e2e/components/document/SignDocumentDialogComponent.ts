import type { Locator, Page } from "@playwright/test";

export class SignDocumentDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Signing Document" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectWitness(label: string): Promise<void> {
    await this.root.getByRole("checkbox", { name: label }).check();
  }

  async signWithPassword(password: string): Promise<void> {
    await this.root.getByRole("button", { name: "Sign", exact: true }).click();
    await this.root.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.root.getByRole("button", { name: "Proceed", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
