import type { Locator, Page } from "@playwright/test";
import { GalleryActionsMenu } from "@/__tests__/e2e/components/gallery/GalleryActionsMenu";
import type { GallerySection } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GallerySidebar } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GalleryVersionHistoryDialog } from "@/__tests__/e2e/components/gallery/GalleryVersionHistoryDialog";

export class GalleryPickerComponent {
  readonly root: Locator;
  readonly addButton: Locator;
  readonly cancelButton: Locator;
  readonly actions: GalleryActionsMenu;
  readonly sidebar: GallerySidebar;
  readonly versionHistoryDialog: GalleryVersionHistoryDialog;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Gallery" });
    this.addButton = this.root.getByRole("button", { name: "Add" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.actions = new GalleryActionsMenu(page);
    this.sidebar = new GallerySidebar(page);
    this.versionHistoryDialog = new GalleryVersionHistoryDialog(page);
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async goToSection(name: GallerySection): Promise<void> {
    await this.sidebar.openSection(name);
  }

  async uploadFile(filePath: string, expectedName: string): Promise<void> {
    await this.sidebar.clickCreate();
    const fileChooserPromise = this.page.waitForEvent("filechooser");
    await this.page.getByRole("menuitem", { name: "Upload Files" }).click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles(filePath);

    await this.root.getByText(expectedName, { exact: true }).first().waitFor({ state: "visible" });
  }

  async selectItem(name: string): Promise<void> {
    await this.root.getByText(name, { exact: true }).last().click();
  }

  async openFolder(name: string): Promise<void> {
    await this.root.getByText(name, { exact: true }).last().dblclick();
    await this.root
      .getByRole("navigation", { name: "Breadcrumbs" })
      .getByRole("button", { name, exact: true })
      .waitFor({ state: "visible" });
  }

  async openVersionHistoryForSelected(): Promise<void> {
    await this.actions.open();
    await this.actions.clickAction("View Version History");
    await this.versionHistoryDialog.waitForOpen();
  }

  async add(): Promise<void> {
    await this.addButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
