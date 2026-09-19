import type { Locator, Page } from "@playwright/test";
import { PublishDialogComponent } from "@/__tests__/e2e/components/myrspace/PublishDialogComponent";
import { ShareDialog } from "@/__tests__/e2e/components/shared/ShareDialog";
import { MoveDialog } from "./MoveDialog";
import { WorkspaceRenameDialog } from "./WorkspaceRenameDialog";
import { awaitTableRefresh } from "./WorkspaceTable";

export type SelectionBarAction =
  | "Duplicate"
  | "Move"
  | "Rename"
  | "Delete"
  | "Export"
  | "CSV"
  | "Revisions"
  | "Add to Favorites"
  | "Share"
  | "Add/Remove Tags"
  | "Publish";

export type WorkspaceSelectionAction = "Duplicate" | "Move" | "Rename" | "Delete" | "Export" | "Revisions" | "Publish";

export class WorkspaceSelectionBar {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("list").filter({
      has: page.getByRole("listitem").filter({ hasText: "Export" }),
    });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private item(name: string): Locator {
    return this.root.getByRole("listitem").filter({ has: this.page.getByText(name, { exact: true }) });
  }

  async isActionVisible(action: SelectionBarAction): Promise<boolean> {
    return (await this.item(action).count()) > 0;
  }

  async clickAction(action: WorkspaceSelectionAction): Promise<void> {
    await this.item(action).click();
  }

  async delete({ viaKeyboard = false }: { viaKeyboard?: boolean } = {}): Promise<void> {
    await this.clickAction("Delete");
    const dialog = this.page.getByRole("dialog", { name: "Confirm deletion" });
    const confirmButton = dialog.getByRole("button", { name: "Confirm" });
    await confirmButton.waitFor({ state: "visible" });
    await awaitTableRefresh(this.page, async () => {
      await Promise.all([
        this.page.waitForResponse((res) => new URL(res.url()).pathname.endsWith("/workspace/ajax/delete")),
        viaKeyboard ? confirmButton.focus().then(() => confirmButton.press("Enter")) : confirmButton.click(),
      ]);
      await dialog.waitFor({ state: "hidden" });
    });
  }

  async toggleFavorite(): Promise<void> {
    const addItem = this.item("Add to Favorites");
    const removeItem = this.item("Remove from Favorites");
    await addItem.or(removeItem).first().waitFor({ state: "visible" });
    const isAdding = await addItem.isVisible();
    const current = isAdding ? addItem : removeItem;
    const flipped = isAdding ? removeItem : addItem;
    await current.click();
    await Promise.race([flipped.waitFor({ state: "visible" }), this.root.waitFor({ state: "hidden" })]);
  }

  async rename(newName: string): Promise<void> {
    await this.clickAction("Rename");
    const dialog = new WorkspaceRenameDialog(this.page);
    await dialog.waitUntilVisible();
    await dialog.submit(newName);
  }

  async move(): Promise<MoveDialog> {
    await this.clickAction("Move");
    const dialog = new MoveDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async exportAsCsv(): Promise<void> {
    await this.item("CSV").getByRole("link", { name: "CSV" }).click();
  }

  async share(): Promise<ShareDialog> {
    await this.item("Share").getByRole("link", { name: "Share" }).click();
    const dialog = new ShareDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async addRemoveTags(): Promise<void> {
    await this.item("Add/Remove Tags").getByRole("link", { name: "Add/Remove Tags" }).click();
  }

  async publish(): Promise<PublishDialogComponent> {
    await this.item("Publish").getByRole("link", { name: "Publish" }).click();
    const dialog = new PublishDialogComponent(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
