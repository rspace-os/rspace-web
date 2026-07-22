import type { Locator, Page } from "@playwright/test";

export class WorkspaceShareDialog {
  readonly root: Locator;
  readonly searchInput: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: /^Share/ });
    this.searchInput = this.root.getByRole("combobox", { name: "Add RSpace users or groups" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async addRecipient(query: string): Promise<void> {
    await this.searchInput.fill(query);
    const option = this.page.getByRole("option", { name: query });
    await option.waitFor({ state: "visible" });
    await option.click();
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
