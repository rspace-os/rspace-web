import type { APIRequestContext } from "@playwright/test";
import { JSDOM } from "jsdom";
import type { MailpitMessage, MailpitMessageSummary } from "../models/mailpit";

export class MailpitClient {
  constructor(private readonly request: APIRequestContext) {}

  private async assertOk(
    res: { ok(): boolean; status(): number; statusText(): string; text(): Promise<string> },
    action: string,
  ): Promise<void> {
    if (!res.ok()) {
      throw new Error(`${action} failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
  }

  /**
   * @param query Mailpit search syntax, e.g. `to:user@example.com` or `subject:"Reset your password"`.
   */
  async listMessages(query?: string): Promise<MailpitMessageSummary[]> {
    const path = query ? `/api/v1/search?query=${encodeURIComponent(query)}` : "/api/v1/messages";
    const res = await this.request.get(path);
    await this.assertOk(res, "listMessages");
    const body = (await res.json()) as { messages: MailpitMessageSummary[] };
    return body.messages;
  }

  async getMessage(id: string): Promise<MailpitMessage> {
    const res = await this.request.get(`/api/v1/message/${id}`);
    await this.assertOk(res, "getMessage");
    return res.json() as Promise<MailpitMessage>;
  }

  async deleteAllMessages(): Promise<void> {
    const res = await this.request.delete("/api/v1/messages");
    await this.assertOk(res, "deleteAllMessages");
  }

  /** Extracts href values from an HTML email body. */
  extractLinks(html: string): string[] {
    const { document } = new JSDOM(html).window;
    return [...document.querySelectorAll("a[href]")].map((a) => a.getAttribute("href") as string);
  }
}
