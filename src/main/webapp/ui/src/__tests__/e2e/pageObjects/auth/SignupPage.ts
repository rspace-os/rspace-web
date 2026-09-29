import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export interface SignupDetails {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  /** Omit to keep the address pre-filled from an invite link. */
  email?: string;
  // Community (cloud) form only.
  affiliation?: string;
  acceptTerms?: boolean;
}

export class SignupPage extends BasePage {
  readonly path = "/signup";

  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly emailInput: Locator;
  readonly affiliationInput: Locator;
  readonly termsCheckbox: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    this.usernameInput = page.getByLabel("Create Username", { exact: true });
    this.passwordInput = page.getByLabel("Create a Password");
    this.confirmPasswordInput = page.getByLabel("Confirm Password", { exact: true });
    this.firstNameInput = page.getByLabel("First Name", { exact: true });
    this.lastNameInput = page.getByLabel("Last Name", { exact: true });
    this.emailInput = page.getByLabel("Email address", { exact: true });
    this.affiliationInput = page.getByLabel("Affiliation", { exact: true });
    this.termsCheckbox = page.getByRole("checkbox", { name: "I agree to these Terms and Conditions" });
    this.submitButton = page.getByRole("button", { name: "Sign up", exact: true });
  }

  async openWithInviteLink(link: string): Promise<void> {
    await this.page.goto(link);
    await this.usernameInput.waitFor();
  }

  async signUp(details: SignupDetails): Promise<void> {
    await this.usernameInput.fill(details.username);
    await this.passwordInput.fill(details.password);
    await this.confirmPasswordInput.fill(details.password);
    await this.firstNameInput.fill(details.firstName);
    await this.lastNameInput.fill(details.lastName);
    if (details.email !== undefined) {
      await this.emailInput.fill(details.email);
    }
    if (details.affiliation !== undefined) {
      await this.affiliationInput.fill(details.affiliation);
    }
    if (details.acceptTerms) {
      await this.termsCheckbox.check();
    }
    await this.submitButton.click();
  }
}
