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

  async selectRecipient(username: string): Promise<void> {
    await this.recipientCombobox.click();
    await this.recipientCombobox.fill(username);
    /*
     * Options are labelled "First Last (username)", so a substring match on the username alone
     * also matches anyone whose own username contains it - "user6f" matches the dynamic-user
     * account "e2eDynUser6fb7179aaab", and .first() then silently transfers to the wrong person.
     * Match the parenthesised username instead; usernames are unique, so this resolves to one
     * option and a future ambiguity fails loudly as a strict-mode violation.
     */
    await this.page.getByRole("option", { name: new RegExp(`\\(${username}\\)`) }).click();
  }

  async confirmTransfer(): Promise<void> {
    await clickAndWaitDetached(this.transferButton, this.root);
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
