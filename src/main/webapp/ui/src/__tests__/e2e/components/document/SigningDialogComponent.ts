import type { Locator, Page } from "@playwright/test";
import { signedStatusLocator } from "./SignedStatus";

export class SigningDialogComponent {
  readonly dialog: Locator;
  private readonly signedStatuses: Locator;

  constructor(page: Page) {
    this.dialog = page
      .getByRole("dialog", { name: "Signing Document" })
      .or(page.getByRole("dialog", { name: "Signing Entry" }));
    this.signedStatuses = signedStatusLocator(page);
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  async signWithoutWitness(password: string): Promise<void> {
    await this.completeSigning(password);
  }

  /** Selects a witness by username from the (already-shared) document's witness list before signing. */
  async signWithWitness(password: string, witnessUsername: string): Promise<void> {
    await this.completeSigning(password, witnessUsername);
  }

  private async completeSigning(password: string, witnessUsername?: string): Promise<void> {
    await this.dialog.getByRole("button", { name: "Sign", exact: true }).click();
    if (witnessUsername !== undefined) {
      await this.dialog.getByRole("checkbox", { name: witnessUsername }).check();
    }
    await this.dialog.getByRole("textbox", { name: "Password:" }).fill(password);
    await this.dialog.getByRole("button", { name: "Proceed" }).click();
    await this.dialog.waitFor({ state: "hidden" });
    await this.signedStatuses.first().waitFor({ state: "visible" });
  }
}
