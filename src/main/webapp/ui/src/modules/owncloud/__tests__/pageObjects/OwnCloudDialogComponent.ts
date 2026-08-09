import type { Locator, Page } from "@playwright/test";

export class OwnCloudDialogComponent {
  readonly root: Locator;
  readonly tree: Locator;
  readonly chooseButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Choose from Owncloud" });
    this.tree = this.root.getByRole("tree");
    this.chooseButton = this.root.getByRole("button", { name: "Choose", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.tree.waitFor({ state: "visible" });
  }

  // FancyTree selection lives on the checkbox, not the (roleless) treeitem.
  async selectFile(fileName: string): Promise<void> {
    await this.tree.getByRole("treeitem", { name: fileName }).locator(".fancytree-checkbox").click();
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
