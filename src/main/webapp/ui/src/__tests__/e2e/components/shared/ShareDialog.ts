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
    // A notebook entry can also display its parent's inherited, disabled permission.
    const select = this.root.getByRole("combobox", {
      name: `Set permission for sharing with ${recipientName}`,
      exact: true,
      disabled: false,
    });
    await select.click();
    await this.page.getByRole("option", { name: PERMISSION_OPTION_LABEL[permission], exact: true }).click();
  }

  async save(): Promise<void> {
    await this.root.getByRole("button", { name: "Save" }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  /** Selects a location within a group's shared folder; the path includes its root folder. */
  async chooseLocation(recipientName: string, folderPath: string[]): Promise<void> {
    if (folderPath.length === 0) throw new Error("A share location must contain at least one folder.");
    const row = this.root.getByRole("row").filter({
      has: this.page.getByRole("combobox", {
        name: `Set permission for sharing with ${recipientName}`,
        exact: true,
        disabled: false,
      }),
    });
    await row.getByRole("button", { name: "Change", exact: true }).click();
    const chooser = this.page.getByRole("dialog", { name: "Select Shared Folder Location", exact: true });
    for (const name of folderPath) {
      await chooser.getByText(name, { exact: true }).click();
    }
    await chooser.getByRole("button", { name: "Select", exact: true }).click();
    await chooser.waitFor({ state: "hidden" });
    await row.getByText(folderPath[folderPath.length - 1], { exact: true }).waitFor({ state: "visible" });
  }

  async close(): Promise<void> {
    await this.root.getByRole("button", { name: "Done" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
