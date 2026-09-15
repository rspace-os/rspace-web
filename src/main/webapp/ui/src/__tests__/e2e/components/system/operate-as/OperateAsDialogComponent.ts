import type { Locator, Page } from "@playwright/test";

export class OperateAsDialogComponent {
  readonly root: Locator;
  readonly userField: Locator;
  readonly incognitoCheckbox: Locator;
  readonly passwordField: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Operate as user" });
    this.userField = this.root.getByRole("textbox", { name: "Please choose a user you want to 'operate as'." });
    this.incognitoCheckbox = this.root.getByRole("checkbox");
    this.passwordField = this.root.getByRole("textbox", {
      name: "Please re-authenticate with your own login password.",
    });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async setUser(username: string): Promise<void> {
    await this.userField.fill(username);
    await this.page.getByRole("listitem").filter({ hasText: username }).click();
  }

  async setIncognito(enabled: boolean): Promise<void> {
    await this.incognitoCheckbox.setChecked(enabled);
  }

  async submit(password: string): Promise<void> {
    await this.passwordField.fill(password);
    await this.root.getByRole("button", { name: "Submit", exact: true }).click();
  }

  errorMessage(textSubstring: string): Locator {
    return this.root.getByText(textSubstring);
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
