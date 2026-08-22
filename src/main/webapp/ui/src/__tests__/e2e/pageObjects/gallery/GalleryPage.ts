import { type Download, expect, type Locator, type Page } from "@playwright/test";
import { GalleryActionsMenu } from "@/__tests__/e2e/components/gallery/GalleryActionsMenu";
import { GalleryEditImageDialog } from "@/__tests__/e2e/components/gallery/GalleryEditImageDialog";
import { GalleryInfoPanel } from "@/__tests__/e2e/components/gallery/GalleryInfoPanel";
import { GalleryMoveDialog } from "@/__tests__/e2e/components/gallery/GalleryMoveDialog";
import type { GallerySection } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GallerySidebar } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GallerySortMenu } from "@/__tests__/e2e/components/gallery/GallerySortMenu";
import { GalleryVersionHistoryDialog } from "@/__tests__/e2e/components/gallery/GalleryVersionHistoryDialog";
import { GalleryViewsMenu } from "@/__tests__/e2e/components/gallery/GalleryViewsMenu";
import { ShareDialog } from "@/__tests__/e2e/components/shared/ShareDialog";
import { DSWImportDialogComponent } from "@/modules/dsw/__tests__/pageObjects/DSWImportDialogComponent";
import { BasePage } from "../BasePage";

export type { GallerySection };

export class GalleryPage extends BasePage {
  readonly path = "/gallery";

  readonly sidebar: GallerySidebar;
  readonly infoPanel: GalleryInfoPanel;
  readonly actions: GalleryActionsMenu;
  readonly views: GalleryViewsMenu;
  readonly sort: GallerySortMenu;
  readonly moveDialog: GalleryMoveDialog;
  readonly versionHistoryDialog: GalleryVersionHistoryDialog;
  readonly editImageDialog: GalleryEditImageDialog;
  readonly shareDialog: ShareDialog;
  readonly fileGrid: Locator;
  readonly searchInput: Locator;
  private readonly searchToggleButton: Locator;
  private readonly filesListingRegion: Locator;

  constructor(page: Page) {
    super(page);
    this.sidebar = new GallerySidebar(page);
    this.infoPanel = new GalleryInfoPanel(page);
    this.actions = new GalleryActionsMenu(page);
    this.views = new GalleryViewsMenu(page);
    this.sort = new GallerySortMenu(page);
    this.moveDialog = new GalleryMoveDialog(page);
    this.versionHistoryDialog = new GalleryVersionHistoryDialog(page);
    this.editImageDialog = new GalleryEditImageDialog(page);
    this.shareDialog = new ShareDialog(page);
    this.fileGrid = page.getByRole("grid", { name: "grid view of files" });
    this.searchInput = page.getByRole("textbox", { name: "Search current folder" });
    this.searchToggleButton = page.getByRole("button", { name: "Search this folder" });
    this.filesListingRegion = page.getByRole("region", { name: "files listing", exact: true });
  }

  override async open(folderId?: string | number): Promise<void> {
    await this.page.goto(folderId !== undefined ? `${this.path}/${folderId}` : this.path);
  }

  async openFile(fileId: string | number): Promise<void> {
    await this.page.goto(`${this.path}/item/${fileId}`);
  }

  async isLoaded(): Promise<void> {
    await this.filesListingRegion.waitFor({ state: "visible" });
    if (!(await this.fileGrid.isVisible().catch(() => false))) {
      await this.views.switchTo("Grid");
      await this.fileGrid.waitFor({ state: "visible" });
    }
  }

  async openSection(section: GallerySection): Promise<void> {
    await this.sidebar.openSection(section);
    await this.isLoaded();
  }

  async openInSection(section: GallerySection): Promise<void> {
    await this.open();
    await this.isLoaded();
    await this.openSection(section);
  }

  fileCell(name: string): Locator {
    return this.fileGrid.getByRole("gridcell", { name, exact: true });
  }

  async waitForFile(name: string): Promise<void> {
    await this.fileCell(name).waitFor({ state: "visible" });
  }

  async selectFile(name: string): Promise<void> {
    const cell = this.fileCell(name);
    await cell.click();
    await expect(cell).toHaveAttribute("aria-selected", "true");
    await this.infoPanel.waitUntilSelected(name);
  }

  async selectMultiple(names: [string, ...string[]]): Promise<void> {
    const [first, ...rest] = names;
    await this.fileCell(first).click();
    for (const name of rest) {
      await this.fileCell(name).click({ modifiers: ["ControlOrMeta"] });
    }
  }

  async openFolder(name: string): Promise<void> {
    await this.fileCell(name).dblclick();
    await this.isLoaded();
  }

  async itemsCount(): Promise<number> {
    return this.fileGrid.getByRole("gridcell").count();
  }

  async createFolder(name: string): Promise<void> {
    await this.sidebar.clickCreate();
    await this.page.getByRole("menuitem", { name: "New Folder" }).click();
    await this.submitNameDialog("New Folder", "Create", name);
    await this.waitForFile(name);
  }

  async renameSelectedTo(newName: string): Promise<void> {
    await this.actions.open();
    await this.actions.clickAction("Rename");
    await this.submitNameDialog("Rename", "Rename", newName);
  }

  async moveSelectedTo(destinationFolder: string): Promise<void> {
    await this.actions.open();
    await this.actions.clickAction("Move");
    await this.moveDialog.waitForOpen();
    await this.moveDialog.moveTo(destinationFolder);
  }

  async downloadSelected(): Promise<Download> {
    await this.actions.open();
    const [download] = await Promise.all([this.page.waitForEvent("download"), this.actions.clickAction("Download")]);
    return download;
  }

  async uploadNewVersionOfSelected(filePath: string): Promise<void> {
    await this.actions.open();
    const fileChooserPromise = this.page.waitForEvent("filechooser");
    await this.actions.clickAction("Upload New Version");
    const fileChooser = await fileChooserPromise;
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/gallery/ajax/uploadFile")),
      fileChooser.setFiles(filePath),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /gallery/ajax/uploadFile failed: ${response.status()} ${response.statusText()}`);
    }
  }

  async openVersionHistoryForSelected(): Promise<void> {
    await this.actions.open();
    await this.actions.clickAction("View Version History");
    await this.versionHistoryDialog.waitForOpen();
  }

  async openEditImageForSelected(): Promise<void> {
    await this.actions.open();
    await this.actions.clickAction("Edit");
    await this.editImageDialog.waitForOpen();
  }

  private async submitNameDialog(heading: string, submitButton: string, value: string): Promise<void> {
    const dialog = this.page.getByRole("dialog").filter({ has: this.page.getByRole("heading", { name: heading }) });
    await dialog.getByRole("textbox", { name: "Name" }).fill(value);
    // The submit response only confirms the mutation; the grid itself only updates once
    // the listing's own follow-up refetch (a separate request) completes.
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/ajax/createFolder") || res.url().includes("/ajax/rename"),
      ),
      this.page.waitForResponse((res) => res.url().includes("/gallery/getUploadedFiles")),
      dialog.getByRole("button", { name: submitButton, exact: true }).click(),
    ]);
    if (!response.ok()) {
      throw new Error(`${heading} submission failed: ${response.status()} ${response.statusText()}`);
    }
    await dialog.waitFor({ state: "hidden" });
    await this.clearStaleAriaHidden();
  }

  // Reaching Create on mobile (GallerySidebar.clickCreate) force-dismisses the sidebar
  // drawer's backdrop while its Create menu is still open. That leaves MUI's modal-stack
  // sibling-hiding stuck: a stray aria-hidden="true" on an ancestor of the file grid that
  // never gets cleared even once every dialog/menu from the flow has closed, silently
  // breaking every getByRole query against the page (confirmed live via MCP — a real
  // accessibility bug, not a test artifact; worth filing separately).
  private async clearStaleAriaHidden(): Promise<void> {
    if ((await this.page.getByRole("dialog").count()) > 0) return;
    await this.page.evaluate(() => {
      let el = document.querySelector('[role="grid"]');
      while (el) {
        if (el.getAttribute("aria-hidden") === "true") el.removeAttribute("aria-hidden");
        el = el.parentElement;
      }
    });
  }

  async searchByName(name: string): Promise<void> {
    if (!(await this.searchInput.isVisible().catch(() => false))) {
      await this.searchToggleButton.click();
    }
    await this.searchInput.fill(name);
  }

  async openDSWImport(alias: string): Promise<DSWImportDialogComponent> {
    await this.sidebar.clickCreate();
    await this.page.getByRole("menuitem", { name: `${alias} DSW / FAIR Wizard` }).click();
    const dialog = new DSWImportDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }
}
