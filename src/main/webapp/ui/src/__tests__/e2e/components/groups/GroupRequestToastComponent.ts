import type { Locator, Page } from "@playwright/test";

/** Legacy toast offering to join, or be PI of, the named group. */
export class GroupRequestToastComponent {
  readonly root: Locator;

  constructor(page: Page, groupName: string) {
    this.root = page.locator(".toast-item").filter({ hasText: `'${groupName}'` });
  }

  async accept(): Promise<void> {
    await this.respond("Accept");
  }

  async decline(): Promise<void> {
    await this.respond("Decline");
  }

  private async respond(name: "Accept" | "Decline"): Promise<void> {
    await this.root.getByRole("button", { name, exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
