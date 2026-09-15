import type { Locator, Page } from "@playwright/test";

export class GalleryMoveDialog {
  readonly dialog: Locator;
  private readonly tree: Locator;

  constructor(page: Page) {
    this.dialog = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Move" }) });
    this.tree = this.dialog.getByRole("tree", { name: "tree view of files" });
  }

  async waitForOpen(): Promise<void> {
    await this.dialog.waitFor({ state: "visible" });
  }

  private treeItemContent(folderName: string): Locator {
    return this.tree.getByRole("treeitem", { name: folderName }).locator(".MuiTreeItem-content");
  }

  async moveTo(folderName: string): Promise<void> {
    await this.treeItemContent(folderName).click();
    await this.dialog.getByRole("button", { name: "Move", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }

  async moveToTopLevel(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Make top-level" }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }
}
