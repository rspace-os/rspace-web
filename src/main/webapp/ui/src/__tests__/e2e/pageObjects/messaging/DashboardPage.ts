import type { Locator, Page } from "@playwright/test";
import { SendMessageDialogComponent } from "@/__tests__/e2e/components/shared/SendMessageDialogComponent";
import { BasePage } from "../BasePage";

/** The Messaging page at /dashboard. */
export class DashboardPage extends BasePage {
  readonly path = "/dashboard";

  readonly sendMessage: SendMessageDialogComponent;

  constructor(page: Page) {
    super(page);
    this.sendMessage = new SendMessageDialogComponent(page);
  }

  override async open(): Promise<void> {
    await this.page.goto(this.path);
    await this.page.getByRole("link", { name: "Open Sent Requests" }).waitFor({ state: "visible" });
  }

  async openCreateMessage(): Promise<SendMessageDialogComponent> {
    await this.page.getByRole("link", { name: "Create Message" }).click();
    await this.sendMessage.waitUntilLoaded();
    return this.sendMessage;
  }

  sentRequest(text: string): Locator {
    return this.page.getByRole("row").filter({ hasText: text });
  }

  async openSentRequests(): Promise<void> {
    await this.page.getByRole("link", { name: "Open Sent Requests" }).click();
    await this.page.getByRole("heading", { name: "My Sent Requests" }).waitFor({ state: "visible" });
  }

  async cancelSentRequest(text: string): Promise<void> {
    const row = this.sentRequest(text);
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/dashboard/ajax/cancelRequest")),
      row.getByRole("link", { name: "Cancel Request", exact: true }).click(),
    ]);
    await row.waitFor({ state: "hidden" });
  }
}
