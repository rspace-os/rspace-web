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

  /**
   * Resolves once the transfer has been applied. The dialog closes as soon as Transfer is clicked
   * while the app sends the request in the background, so the dialog closing proves nothing.
   */
  async confirmTransfer(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => new URL(res.url()).pathname.endsWith("/api/inventory/v1/bulk") && res.request().method() === "POST",
      ),
      clickAndWaitDetached(this.transferButton, this.root),
    ]);
    if (!response.ok()) {
      throw new Error(`Transfer failed: ${response.status()} ${await response.text()}`);
    }
    const body = (await response.json()) as {
      status: string;
      results: Array<{ error: { errors: string[] } | null }>;
    };
    if (body.status !== "COMPLETED") {
      const errors = body.results.flatMap((result) => result.error?.errors ?? []);
      throw new Error(`Transfer ${body.status}: ${errors.join("; ")}`);
    }
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
