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

  async send({ to, type, text }: { to?: string; type?: MessageTypeLabel; text?: string }): Promise<void> {
    if (type) {
      await this.requestType.selectOption({ label: type });
    }
    if (to) {
      await this.toField.fill(to);
      await this.root.getByRole("listitem").filter({ hasText: to }).first().click();
    }
    if (text) {
      await this.messageField.fill(text);
    }
    const [res] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.request().method() === "POST" && res.url().includes("/messaging/ajax/create"),
      ),
      this.root.getByRole("button", { name: "Send", exact: true }).click(),
    ]);
    if (!res.ok()) {
      throw new Error(`Sending message failed: ${res.status()}`);
    }
    // Validation failures return 200 with the form re-rendered; its <form:errors> spans have no role, only this class.
    if ((await res.text()).includes("error")) {
      const reasons = this.root.locator(".error");
      await reasons.first().waitFor({ state: "visible" });
      throw new Error(`Sending message was rejected: ${(await reasons.allInnerTexts()).join("; ")}`);
    }
    await this.root.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
