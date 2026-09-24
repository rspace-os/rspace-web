import { expect, type Locator, type Page } from "@playwright/test";
import { signedStatusLocator } from "./SignedStatus";

export class SigningDialogComponent {
  readonly dialog: Locator;
  private readonly signedStatuses: Locator;
  private readonly toolbarMounted: Locator;

  constructor(private readonly page: Page) {
    this.dialog = page
      .getByRole("dialog", { name: "Signing Document" })
      .or(page.getByRole("dialog", { name: "Signing Entry" }));
    this.signedStatuses = signedStatusLocator(page);
    this.toolbarMounted = page.locator("#toolbar2").getByRole("button").first();
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

    const reloaded = this.page.waitForEvent("load");
    reloaded.catch(() => undefined);
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/proceedSigning")),
      this.dialog.getByRole("button", { name: "Proceed" }).click(),
    ]);
    expect(response.ok(), `Signing returned HTTP ${response.status()}`).toBe(true);
    const body = (await response.json()) as { errorMsg?: { errorMessages?: string[] } | null };
    if (body.errorMsg) {
      throw new Error(
        `Signing was rejected: ${body.errorMsg.errorMessages?.join("; ") ?? JSON.stringify(body.errorMsg)}`,
      );
    }
    await this.dialog.waitFor({ state: "hidden" });
    await reloaded;
    await this.signedStatuses.first().waitFor({ state: "visible" });
    await this.toolbarMounted.waitFor({ state: "visible" });
  }
}
