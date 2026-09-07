import type { Locator, Page } from "@playwright/test";

export class InviteMembersDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog");
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.getByRole("heading", { name: "Invite members" }).waitFor({ state: "visible" });
  }

  async invite(username: string): Promise<void> {
    await this.root.getByRole("searchbox").first().fill(username);
    await this.root.locator(`[data-test-id="select-option-${username}"]`).click();
    await this.root.locator('[data-test-id="button-add-selected"]').click();
    await this.root.getByRole("button", { name: "Invite", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
