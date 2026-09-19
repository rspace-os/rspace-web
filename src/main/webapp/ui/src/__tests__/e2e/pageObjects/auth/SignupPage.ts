import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export interface SignupDetails {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
}

export class SignupPage extends BasePage {
  readonly path = "/signup";

  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly emailInput: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    this.usernameInput = page.getByLabel("Create Username", { exact: true });
    this.passwordInput = page.getByLabel("Create a Password");
    this.confirmPasswordInput = page.getByLabel("Confirm Password", { exact: true });
    this.firstNameInput = page.getByLabel("First Name", { exact: true });
    this.lastNameInput = page.getByLabel("Last Name", { exact: true });
    this.emailInput = page.getByLabel("Email address", { exact: true });
    this.submitButton = page.getByRole("button", { name: "Sign up" });
  }

  async signUp(details: SignupDetails): Promise<void> {
    await this.usernameInput.fill(details.username);
    await this.passwordInput.fill(details.password);
    await this.confirmPasswordInput.fill(details.password);
    await this.firstNameInput.fill(details.firstName);
    await this.lastNameInput.fill(details.lastName);
    await this.emailInput.fill(details.email);
    await this.submitButton.click();
  }
}
