import type { Locator, Page } from "@playwright/test";

export class MessagesAndRequestsDialogComponent {
  readonly root: Locator;
  readonly statusSelect: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Messages and Requests" });
    this.statusSelect = this.root.locator('select[name="messageStatus"]');
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

  messagesWithSubject(subject: string): Locator {
    return this.root.getByText(subject);
  }

  async openLinkedRecord(recordName: string): Promise<void> {
    await this.root.getByRole("link", { name: recordName, exact: true }).click();
  }

  /** Waits for the AJAX-rendered list; messages_ajax.jsp renders this container whether or not there are messages. */
  async waitUntilListed(): Promise<void> {
    await this.root.locator("#messageListContents").waitFor({ state: "attached" });
  }

  message(text: string): Locator {
    return this.root.getByRole("row").filter({ hasText: text });
  }

  async reply(to: string, replyText: string): Promise<void> {
    const row = this.message(to);
    await row.getByRole("textbox").fill(replyText);
    const [response] = await Promise.all([
      this.root.page().waitForResponse((res) => res.url().includes("/dashboard/ajax/messageReply")),
      row.getByRole("link", { name: "Reply", exact: true }).click(),
    ]);
    const body = (await response.json()) as { data?: string };
    if (body.data !== "Success") {
      throw new Error(`Reply to "${to}" was not accepted: ${JSON.stringify(body)}`);
    }
  }

  async dismiss(text: string): Promise<void> {
    const row = this.message(text);
    await row.getByRole("link", { name: "Dismiss", exact: true }).click();
    await row.waitFor({ state: "hidden" });
  }
}
