import type { Locator, Page } from "@playwright/test";

export class RecordInfoDialog {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Info" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "OK" }).click();
    await this.root.waitFor({ state: "detached" });
  }

  async field(name: string): Promise<string> {
    const labelCell = this.root.getByRole("cell", { name: `${name}:`, exact: true });
    const value = await labelCell.evaluate((el) => el.nextElementSibling?.textContent?.trim() ?? "");
    return value;
  }

  get relatedInventoryItemsContent(): Locator {
    // jQuery injects this section without a role or label; the class is its only stable hook.
    return this.root.locator(".relatedInventoryItemsContent");
  }

  async isShared(): Promise<boolean> {
    const text = await this.root.innerText();
    return !text.includes("This document is not shared.");
  }

  async isPublished(): Promise<boolean> {
    const text = await this.root.innerText();
    return !text.includes("This document is not published.");
  }
}
