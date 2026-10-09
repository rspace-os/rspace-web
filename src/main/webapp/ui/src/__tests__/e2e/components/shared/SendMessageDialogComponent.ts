import type { Locator, Page } from "@playwright/test";

export type MessageTypeLabel =
  | "Basic message"
  | "Create a Collaboration Group"
  | "Review document"
  | "Message to all users";

export class SendMessageDialogComponent {
  readonly root: Locator;
  readonly toField: Locator;
  readonly requestType: Locator;
  readonly messageField: Locator;
  readonly typeOptions: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Send a Message" });
    this.toField = this.root.getByRole("textbox", { name: "To" });
    this.requestType = this.root.getByRole("combobox", { name: "Request Type" });
    this.messageField = this.root.getByRole("textbox", { name: /^Optional Message/ });
    this.typeOptions = this.requestType.getByRole("option");
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async waitUntilLoaded(): Promise<void> {
    await this.requestType.waitFor({ state: "visible" });
  }

  async openFromToolbar(): Promise<void> {
    await this.page.getByRole("button", { name: "Send a message", exact: true }).click();
    await this.waitUntilLoaded();
  }

  async sendTo(username: string): Promise<void> {
    await this.toField.fill(username);
    await this.root.getByRole("listitem").filter({ hasText: username }).first().click();
    await this.root.getByRole("button", { name: "Send", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async send({ to, type, text }: { to?: string; type?: MessageTypeLabel; text: string }): Promise<void> {
    if (type) {
      await this.requestType.selectOption({ label: type });
    }
    if (to) {
      await this.toField.fill(to);
      await this.root.getByRole("listitem").filter({ hasText: to }).first().click();
    }
    await this.messageField.fill(text);
    const [res] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.request().method() === "POST" && res.url().includes("/messaging/ajax/create"),
      ),
      this.root.getByRole("button", { name: "Send", exact: true }).click(),
    ]);
    if (!res.ok()) {
      throw new Error(`Sending message failed: ${res.status()}`);
    }
    await this.root.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
