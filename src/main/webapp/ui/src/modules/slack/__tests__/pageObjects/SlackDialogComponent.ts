import type { Locator, Page } from "@playwright/test";

export class SlackDialogComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog");
  }

  async open(): Promise<void> {
    await this.page.goto("/apps");
    await this.page.locator('div[aria-label="Slack"]').click();
    await this.root.waitFor({ state: "visible" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Close" }).click();
    await this.root.waitFor({ state: "detached" });
  }

  async connectAndSaveChannel(): Promise<void> {
    await this.root.getByRole("button", { name: "Add", exact: true }).click();

    const pendingForm = this.root.locator("form").last();
    await pendingForm.getByRole("textbox", { name: "RSpace Label" }).waitFor({ state: "visible", timeout: 15_000 });
    await pendingForm.getByRole("button", { name: "Save" }).click();
    await pendingForm.getByRole("button", { name: "Remove" }).waitFor({ state: "visible", timeout: 10_000 });
  }

  /** Text from the DescriptionList of a saved channel card, e.g. its workspace name. */
  savedChannelText(text: string): Locator {
    return this.root.getByText(text);
  }
}
