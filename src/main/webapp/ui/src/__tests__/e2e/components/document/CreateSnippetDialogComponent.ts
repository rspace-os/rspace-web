import type { Locator, Page } from "@playwright/test";

export class CreateSnippetDialogComponent {
  readonly dialog: Locator;

  constructor(page: Page) {
    this.dialog = page.getByRole("dialog", { name: "Create a snippet" });
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  async create(name: string): Promise<void> {
    const frame = this.dialog.frameLocator("iframe");
    await frame.locator("#snippet_name").fill(name);
    await this.dialog.getByRole("button", { name: "Create", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }
}
