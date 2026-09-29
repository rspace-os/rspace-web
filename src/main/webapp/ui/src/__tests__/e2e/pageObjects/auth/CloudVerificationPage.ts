import type { Locator, Page } from "@playwright/test";

/** Community (cloud) pages reached from emailed links: signup activation and email-change confirmation. */
export class CloudVerificationPage {
  constructor(private readonly page: Page) {}

  get heading(): Locator {
    return this.page.getByRole("heading", { level: 2 });
  }

  async activateAccount(link: string): Promise<void> {
    await this.page.goto(link);
    await this.page.getByRole("button", { name: "Join RSpace!", exact: true }).click();
    await this.page.waitForURL((url) => url.pathname === "/cloud/signup/accountActivationComplete");
  }

  async confirmEmailChange(link: string): Promise<void> {
    await this.page.goto(link);
    await this.page.getByRole("button", { name: "Change Email", exact: true }).click();
    await this.page.waitForURL((url) => url.pathname === "/cloud/verifyEmailChange/emailChangeConfirmed");
  }
}
