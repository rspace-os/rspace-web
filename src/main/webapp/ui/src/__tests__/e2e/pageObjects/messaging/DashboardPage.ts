import type { Locator, Page } from "@playwright/test";
import { SendMessageDialogComponent } from "@/__tests__/e2e/components/shared/SendMessageDialogComponent";
import { env } from "@/__tests__/e2e/env";
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
    const [res] = await Promise.all([
      this.page.waitForResponse((r) => r.url().includes("/dashboard/ajax/listMyRequests")),
      this.page.getByRole("link", { name: "Open Sent Requests" }).click(),
    ]);
    if (!res.ok()) {
      throw new Error(`Listing sent requests failed: ${res.status()}`);
    }
    // myrequests_ajax.jsp renders this container whether or not there are requests; the heading only when there are.
    await this.page.locator("#myrequestListContents").waitFor({ state: "attached" });
  }

  async cancelSentRequestQuietly(text: string): Promise<void> {
    const rowId = await this.sentRequest(text).first().getAttribute("id");
    const messageOrRequestId = rowId?.replace("myrequestID_", "");
    if (!messageOrRequestId) {
      throw new Error(`No sent request id found for "${text}"`);
    }
    const res = await this.page.request.post("/dashboard/ajax/cancelRequest", {
      headers: { Referer: env.baseURL },
      form: { messageOrRequestId, quiet: "true" },
    });
    if (!res.ok()) {
      throw new Error(`Cancelling sent request ${messageOrRequestId} failed: ${res.status()}`);
    }
  }
}
