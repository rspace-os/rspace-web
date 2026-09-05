import type { Locator, Page } from "@playwright/test";

export class WitnessDocumentDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Witnessing Document" });
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
}
