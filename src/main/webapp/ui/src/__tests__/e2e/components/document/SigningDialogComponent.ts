import { expect, type Locator, type Page } from "@playwright/test";
import { signedStatusLocator } from "./SignedStatus";

/** Signing ends in a page reload, so done means the reloaded page shows the signature. */
export async function proceedWithSigning(page: Page, dialog: Locator): Promise<void> {
  const reloaded = page.waitForEvent("load");
  reloaded.catch(() => undefined);
  const [response] = await Promise.all([
    page.waitForResponse((res) => res.url().includes("/ajax/proceedSigning")),
    dialog.getByRole("button", { name: "Proceed", exact: true }).click(),
  ]);
  expect(response.ok(), `Signing returned HTTP ${response.status()}`).toBe(true);
  const body = (await response.json()) as { errorMsg?: { errorMessages?: string[] } | null };
  if (body.errorMsg) {
    throw new Error(
      `Signing was rejected: ${body.errorMsg.errorMessages?.join("; ") ?? JSON.stringify(body.errorMsg)}`,
    );
  }
  await dialog.waitFor({ state: "hidden" });
  await reloaded;
  await signedStatusLocator(page).first().waitFor({ state: "visible" });
  await page.locator("#toolbar2").getByRole("button").first().waitFor({ state: "visible" });
}

export class SigningDialogComponent {
  readonly dialog: Locator;

  constructor(private readonly page: Page) {
    this.dialog = page
      .getByRole("dialog", { name: "Signing Document" })
      .or(page.getByRole("dialog", { name: "Signing Entry" }));
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
    await proceedWithSigning(this.page, this.dialog);
  }
}
