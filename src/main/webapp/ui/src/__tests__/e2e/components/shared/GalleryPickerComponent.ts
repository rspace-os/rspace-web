import { expect, type Locator, type Page } from "@playwright/test";
import {
  type FilestoreCredentials,
  FilestoreLoginDialog,
} from "@/__tests__/e2e/components/gallery/FilestoreLoginDialog";
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
  private readonly loginDialog: FilestoreLoginDialog;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Gallery" });
    this.addButton = this.root.getByRole("button", { name: "Add", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.actions = new GalleryActionsMenu(page);
    this.sidebar = new GallerySidebar(page);
    this.versionHistoryDialog = new GalleryVersionHistoryDialog(page);
    this.loginDialog = new FilestoreLoginDialog(page);
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

  /**
   * The first is clicked, the rest added with Ctrl/Cmd-click. Forced: filestore items are marked aria-disabled
   * although a mouse user can select them.
   */
  async selectFilestoreItems(names: [string, ...string[]]): Promise<void> {
    for (const [index, name] of names.entries()) {
      const cell = this.root.getByRole("gridcell", { name, exact: true });
      await cell.click({ force: true, modifiers: index === 0 ? [] : ["ControlOrMeta"] });
      await expect(cell).toHaveAttribute("aria-selected", "true");
    }
  }

  /** Filestores open like folders; pass credentials when their file system asks for a login. */
  async openFolder(name: string, credentials?: FilestoreCredentials): Promise<void> {
    await this.root.getByText(name, { exact: true }).last().dblclick();
    if (credentials) {
      await this.loginDialog.login(credentials);
    }
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
