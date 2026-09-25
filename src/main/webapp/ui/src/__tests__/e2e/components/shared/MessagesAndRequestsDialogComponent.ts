import type { Locator, Page } from "@playwright/test";

export class MessagesAndRequestsDialogComponent {
  readonly root: Locator;
  readonly statusSelect: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Messages and Requests" });
    this.statusSelect = this.root.locator('select[name="messageStatus"]');
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async acceptFirstRequest(): Promise<void> {
    await this.statusSelect.first().selectOption("ACCEPTED");
    const updateAndReply = this.root.getByRole("link", { name: "Update & Reply" });
    await updateAndReply.click();
    await updateAndReply.waitFor({ state: "hidden" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async openLinkedRecord(recordName: string): Promise<void> {
    await this.root.getByRole("link", { name: recordName, exact: true }).click();
  }
}
