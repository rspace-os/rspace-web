import type { Locator, Page } from "@playwright/test";

export class InternalLinkDialogComponent {
  readonly dialog: Locator;

  constructor(page: Page) {
    this.dialog = page.getByRole("dialog", { name: "Internal Link" });
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  async insertLinkTo(globalId: string): Promise<void> {
    const frame = this.dialog.frameLocator("iframe");
    await frame.getByRole("textbox", { name: "Search with query, or by global ID..." }).fill(globalId);
    await frame.locator("#searchBtn").click();
    await frame.getByText(`Global Id: ${globalId},`).click();
    await this.dialog.getByRole("button", { name: "Insert", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }
}
