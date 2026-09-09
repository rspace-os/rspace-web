import type { Locator, Page } from "@playwright/test";

export type SharePermission = "READ" | "EDIT" | "UNSHARE";

const PERMISSION_OPTION_LABEL: Record<SharePermission, string> = {
  READ: "Read",
  EDIT: "Edit",
  UNSHARE: "Unshare",
};

export class ShareDialog {
  readonly root: Locator;
  readonly searchInput: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Share" });
    this.searchInput = this.root.getByRole("combobox", { name: "Add RSpace users or groups" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async addRecipient(query: string): Promise<void> {
    await this.search(query);
    const option = this.recipientOption(query);
    await option.waitFor({ state: "visible" });
    await option.click();
  }

  /** Searches the "Add RSpace users or groups" box; pair with recipientOption/isOptionDisabled. */
  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
  }

  private recipientOption(name: string): Locator {
    return this.page.getByRole("option", { name });
  }

  /** Whether a recipient option — already located via search() — is disabled (non-selectable). */
  async isOptionDisabled(name: string): Promise<boolean> {
    const option = this.recipientOption(name);
    await option.waitFor({ state: "visible" });
    return (await option.getAttribute("aria-disabled")) === "true";
  }

  async setPermission(recipientName: string, permission: SharePermission): Promise<void> {
    const select = this.root.getByRole("combobox", { name: `Set permission for sharing with ${recipientName}` });
    await select.click();
    await this.page.getByRole("option", { name: PERMISSION_OPTION_LABEL[permission], exact: true }).click();
  }

  async save(): Promise<void> {
    await this.root.getByRole("button", { name: "Save" }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Done" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
