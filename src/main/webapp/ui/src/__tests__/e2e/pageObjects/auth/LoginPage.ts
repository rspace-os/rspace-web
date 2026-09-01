import type { Locator, Page } from "@playwright/test";
import { PublicNavComponent } from "@/__tests__/e2e/components/navigation/PublicNavComponent";
import { BasePage } from "../BasePage";
import { RequestPasswordResetPage } from "./RequestPasswordResetPage";
import { RequestUsernameReminderPage } from "./RequestUsernameReminderPage";

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
    // Fill the autofocus field last: the legacy login page may restore focus and clear it while
    // browser password handling settles.
    await this.passwordInput.fill(password);
    await this.usernameInput.fill(username);
    if ((await this.usernameInput.inputValue()) !== username || (await this.passwordInput.inputValue()) !== password) {
      throw new Error("Login credentials were not retained by the form");
    }
    await Promise.all([
      this.submitButton.click(),
      Promise.any([
        this.page.waitForURL((url) => !url.pathname.endsWith("/login")),
        this.invalidCredentialsError.waitFor({ state: "visible" }),
      ]),
    ]);
  }

  async clickForgotUsername(): Promise<RequestUsernameReminderPage> {
    await this.page.getByRole("link", { name: "Forgotten your username?" }).click();
    return new RequestUsernameReminderPage(this.page);
  }

  async clickForgotPassword(): Promise<RequestPasswordResetPage> {
    await this.page.getByRole("link", { name: "Forgotten your password?" }).click();
    return new RequestPasswordResetPage(this.page);
  }
}
