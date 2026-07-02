import type { Locator, Page } from "@playwright/test";
import { PublicNavComponent } from "@/__tests__/e2e/components/navigation/PublicNavComponent";
import { BasePage } from "../BasePage";

export class LoginPage extends BasePage {
  readonly path = "/login";

  readonly publicNav: PublicNavComponent;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly invalidCredentialsError: Locator;

  constructor(page: Page) {
    super(page);
    this.publicNav = new PublicNavComponent(page);
    this.usernameInput = page.getByRole("textbox", { name: "User" });
    this.passwordInput = page.getByRole("textbox", { name: "Password" });
    this.submitButton = page.getByRole("button", { name: "Log in" });
    this.invalidCredentialsError = page.getByText("Invalid username or password, please try again.");
  }

  async login(username: string, password: string): Promise<void> {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }

  async clickForgotUsername(): Promise<void> {
    await this.page.getByRole("link", { name: "Forgotten your username?" }).click();
  }

  async clickForgotPassword(): Promise<void> {
    await this.page.getByRole("link", { name: "Forgotten your password?" }).click();
  }
}
