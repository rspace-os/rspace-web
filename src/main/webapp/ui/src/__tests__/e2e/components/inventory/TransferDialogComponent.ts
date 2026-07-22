import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export class TransferDialogComponent {
  readonly root: Locator;
  private readonly recipientCombobox: Locator;
  private readonly cancelButton: Locator;
  private readonly transferButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Transfer Ownership" });
    this.recipientCombobox = this.root.getByRole("combobox");
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.transferButton = this.root.getByRole("button", { name: "Transfer", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectRecipient(query: string): Promise<void> {
    await this.recipientCombobox.click();
    await this.recipientCombobox.fill(query);
    await this.page.getByRole("option", { name: query }).first().click();
  }

  async confirmTransfer(): Promise<void> {
    await clickAndWaitDetached(this.transferButton, this.root);
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
