import type { Locator, Page } from "@playwright/test";

export class AttachmentsSection {
  readonly heading: Locator;
  readonly filesButton: Locator;
  readonly inventoryItemsButton: Locator;

  constructor(page: Page) {
    this.heading = page.getByRole("heading", { name: "Attachments", level: 2 });
    this.filesButton = page.getByRole("button", { name: "Files" });
    this.inventoryItemsButton = page.getByRole("button", { name: "Inventory Items" });
  }

  async openFiles(): Promise<void> {
    await this.filesButton.click();
  }

  async openInventoryItems(): Promise<void> {
    await this.inventoryItemsButton.click();
  }
}
