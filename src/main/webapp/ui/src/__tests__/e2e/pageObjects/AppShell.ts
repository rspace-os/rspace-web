import type { Page } from "@playwright/test";
import { type AppUser, USERS } from "../users";

/**
 * Shared app-shell navigation reused by every integration e2e: log in, open a
 * document into the TinyMCE editor. URLs are relative (baseURL is set in
 * playwright-e2e.config.ts).
 */
export class AppShell {
  constructor(private readonly page: Page) {}

  async login(user: AppUser = USERS.user1a): Promise<void> {
    await this.page.goto("/login");
    await this.page.getByRole("textbox", { name: "User" }).fill(user.username);
    await this.page.getByRole("textbox", { name: "Password" }).fill(user.password);
    await this.page.getByRole("button", { name: "Log in" }).click();
    await this.page.waitForURL("**/workspace");
  }

  async openBasicDocument(): Promise<void> {
    await this.page.locator('[data-test-id="create-btn"]').click();
    await this.page.locator('[data-test-id="create-btn-basic-document"]').click();
    await this.page.waitForURL("**/workspace/editor/structuredDocument/**");
  }
}
