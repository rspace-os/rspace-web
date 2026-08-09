import type { Locator, Page } from "@playwright/test";

export class NextcloudDialogComponent {
  readonly root: Locator;
  readonly tree: Locator;
  readonly chooseButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Choose from Nextcloud" });
    this.tree = this.root.getByRole("tree");
    this.chooseButton = this.root.getByRole("button", { name: "Choose", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.tree.waitFor({ state: "visible" });
    await this.tree.getByRole("treeitem", { name: "Loading..." }).waitFor({ state: "hidden" });
  }

  // FancyTree selection lives on the checkbox, not the (roleless) treeitem.
  async selectFile(fileName: string): Promise<void> {
    await this.tree.getByRole("treeitem", { name: fileName }).locator(".fancytree-checkbox").click();
  }

  async selectFirstFile(): Promise<string> {
    const items = this.tree.getByRole("treeitem");
    const count = await items.count();
    for (let i = 0; i < count; i++) {
      const item = items.nth(i);
      const isFolder = (await item.getByRole("button").count()) > 0;
      if (isFolder) continue;
      const name = (await item.textContent())?.trim();
      if (!name) throw new Error("selectFirstFile: matched a file treeitem with no text content");
      await item.locator(".fancytree-checkbox").click();
      return name;
    }
    throw new Error("selectFirstFile: no file (non-folder) treeitem found");
  }

  async clickChoose(): Promise<void> {
    await this.chooseButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async close(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}
