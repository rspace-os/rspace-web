import type { Locator, Page } from "@playwright/test";

export class SlackShareDialogComponent {
  readonly toolbarButton: Locator;
  readonly root: Locator;
  readonly channelSelect: Locator;
  readonly messageInput: Locator;

  constructor(page: Page) {
    this.toolbarButton = page.getByRole("button", { name: "Send message on Slack" });
    this.root = page.getByRole("dialog", { name: "Send message to external messaging platform" });
    this.channelSelect = this.root.locator("select.channelSelect");
    this.messageInput = this.root.locator("textarea.extMessageRequestMessage");
  }

  async open(): Promise<void> {
    await this.toolbarButton.click();
    await this.root.waitFor({ state: "visible" });
  }

  async send(channelConnectorName: string, message: string): Promise<void> {
    await this.channelSelect.selectOption({ label: channelConnectorName });
    await this.messageInput.fill(message);

    await this.root.locator(".ui-dialog-buttonpane").getByRole("button", { name: "Send", exact: true }).click();
    await this.root.page().getByText("Message sent").waitFor({ state: "visible" });
    await this.root.waitFor({ state: "hidden" });
  }
}
