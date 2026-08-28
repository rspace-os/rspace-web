import type { FrameLocator, Locator, Page } from "@playwright/test";

export class GitHubDialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;
  readonly insertButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "GitHub Link" });
    this.frame = this.root.frameLocator("iframe");
    this.insertButton = this.root.getByRole("button", { name: "Insert" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.frame.locator("#file_tree").waitFor({ state: "visible" });
  }

  async selectRepository(fullName: string): Promise<void> {
    const link = this.frame.getByRole("link", { name: fullName, exact: true });
    await this.frame.locator("li.directory.collapsed", { has: link }).waitFor({ state: "visible" });
    await link.evaluate((el: HTMLElement) => el.click());
    await this.frame.locator("#file_tree li.directory.expanded").waitFor({ state: "visible" });
  }

  /** Selects a file (or folder) below an already-expanded repository. */
  async selectPath(name: string): Promise<void> {
    await this.frame.getByRole("link", { name, exact: true }).evaluate((el: HTMLElement) => el.click());
  }

  async clickInsert(): Promise<void> {
    await this.insertButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
