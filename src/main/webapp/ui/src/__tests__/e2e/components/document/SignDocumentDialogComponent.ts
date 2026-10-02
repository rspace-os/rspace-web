import type { Locator, Page } from "@playwright/test";
import { AppriseAlertComponent } from "@/__tests__/e2e/components/system/AppriseAlertComponent";
import { proceedWithSigning } from "./SigningDialogComponent";

export class SignDocumentDialogComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Signing Document" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectWitness(label: string): Promise<void> {
    await this.root.getByRole("checkbox", { name: label }).check();
  }

  async signWithPassword(password: string): Promise<void> {
    await this.root.getByRole("button", { name: "Sign", exact: true }).click();
    await this.retryWithPassword(password);
  }

  /** Submits an incorrect password; the dialog stays open behind the resulting alert. Retry with retryWithPassword. */
  async signWithPasswordExpectingInvalid(password: string): Promise<AppriseAlertComponent> {
    await this.root.getByRole("button", { name: "Sign", exact: true }).click();
    await this.passwordInput.fill(password);
    await this.root.getByRole("button", { name: "Proceed", exact: true }).click();
    const alert = new AppriseAlertComponent(this.page);
    await alert.waitUntilVisible();
    return alert;
  }

  /** Also the retry after signWithPasswordExpectingInvalid(). */
  async retryWithPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password);
    await proceedWithSigning(this.page, this.root);
  }

  private get passwordInput(): Locator {
    return this.root.getByRole("textbox", { name: "Password:" });
  }
}
