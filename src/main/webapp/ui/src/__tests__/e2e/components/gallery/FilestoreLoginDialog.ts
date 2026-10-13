import type { Locator, Page } from "@playwright/test";
import { responseTo } from "@/__tests__/e2e/responses";

export interface FilestoreCredentials {
  username: string;
  password: string;
}

export class FilestoreLoginDialog {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Filestore Login" });
  }

  async login({ username, password }: FilestoreCredentials): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.root.getByRole("textbox", { name: "Username" }).fill(username);
    await this.root.getByRole("textbox", { name: "Password" }).fill(password);
    await responseTo(
      this.page,
      "POST",
      ({ pathname }) => pathname.startsWith("/api/v1/gallery/filesystems/") && pathname.endsWith("/login"),
      () => this.root.getByRole("button", { name: "Login" }).click(),
    );
    await this.root.waitFor({ state: "hidden" });
  }
}
