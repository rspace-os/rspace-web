import type { FrameLocator, Locator, Page } from "@playwright/test";

export class EgnyteDialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Egnyte File Picker" });
    this.frame = this.root.frameLocator("iframe");
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async waitForPicker(): Promise<void> {
    await this.frame.locator("li.eg-picker-item").first().waitFor({ state: "visible", timeout: 15_000 });
  }

  private itemRow(name: string): Locator {
    return this.frame.locator("li.eg-picker-item", { has: this.frame.getByTitle(name, { exact: true }) });
  }

  async selectFile(name: string): Promise<void> {
    await this.itemRow(name).locator('input[type="checkbox"]').click();
  }

  async confirmSelection(): Promise<void> {
    await this.frame.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "detached" });
  }
}
