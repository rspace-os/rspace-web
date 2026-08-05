import type { Locator, Page } from "@playwright/test";

export class WorkspaceRecordInfoDialog {
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
    const row = this.root.getByRole("row", { name: `${name}:` });
    const text = await row.innerText();
    return text.replace(`${name}:`, "").trim();
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
