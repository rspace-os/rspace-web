import { type APIRequestContext, expect } from "@playwright/test";
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

  /** Polls until an email with `subject` reaches `to`, then returns it. */
  async waitForMessage(to: string, subject: string, timeoutMs = 15_000): Promise<MailpitMessage> {
    let summary: MailpitMessageSummary | undefined;
    await expect
      .poll(
        async () => {
          summary = (await this.listMessages(`to:${to}`)).find((m) => m.Subject === subject);
          return summary?.ID;
        },
        { message: `"${subject}" email to ${to}`, timeout: timeoutMs },
      )
      .toBeDefined();
    return this.getMessage(summary?.ID ?? "");
  }

  /** Waits for the email as {@link waitForMessage}, then returns its first link whose URL contains `pathFragment`. */
  async waitForLink(to: string, subject: string, pathFragment: string, timeoutMs = 15_000): Promise<string> {
    const message = await this.waitForMessage(to, subject, timeoutMs);
    const link = this.extractLinks(message.HTML).find((href) => href.includes(pathFragment));
    if (!link) {
      throw new Error(`"${subject}" email to ${to} has no link containing "${pathFragment}": ${message.HTML}`);
    }
    return link;
  }

  /** Extracts href values from an HTML email body. */
  extractLinks(html: string): string[] {
    const { document } = new JSDOM(html).window;
    return [...document.querySelectorAll("a[href]")].map((a) => a.getAttribute("href") as string);
  }

  /** Extracts the visible plain text from an HTML email body. */
  extractText(html: string): string {
    const { document } = new JSDOM(html).window;
    return document.body.textContent ?? "";
  }
}
