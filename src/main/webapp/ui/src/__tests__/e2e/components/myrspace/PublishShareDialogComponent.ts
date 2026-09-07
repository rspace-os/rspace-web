import type { Locator, Page } from "@playwright/test";

export type SharePermission = "NONE" | "READ" | "WRITE";
export type WorldSharePermission = Exclude<SharePermission, "WRITE">;

export class PublishShareDialogComponent {
  readonly root: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Configure access to Forms" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async setGroup(permission: SharePermission): Promise<void> {
    const row = this.root.locator("#templateShareConfig tr").filter({ hasText: "Group" });
    await row.getByLabel(permission, { exact: true }).check();
  }

  async setWorld(permission: WorldSharePermission): Promise<void> {
    const row = this.root.locator("#templateShareConfig tr").filter({ hasText: "World" });
    await row.getByLabel(permission, { exact: true }).check();
  }

  async ok(): Promise<void> {
    await this.root.getByRole("button", { name: "OK", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
