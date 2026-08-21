import { expect, type Locator, type Page } from "@playwright/test";
import { GalleryActionsMenu } from "@/__tests__/e2e/components/gallery/GalleryActionsMenu";
import { GalleryInfoPanel } from "@/__tests__/e2e/components/gallery/GalleryInfoPanel";
import type { GallerySection } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GallerySidebar } from "@/__tests__/e2e/components/gallery/GallerySidebar";
import { GallerySortMenu } from "@/__tests__/e2e/components/gallery/GallerySortMenu";
import { GalleryViewsMenu } from "@/__tests__/e2e/components/gallery/GalleryViewsMenu";
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

  async openFolder(name: string): Promise<void> {
    await this.fileCell(name).dblclick();
    await this.isLoaded();
  }

  async searchByName(name: string): Promise<void> {
    if (!(await this.searchInput.isVisible().catch(() => false))) {
      await this.searchToggleButton.click();
    }
    await this.searchInput.fill(name);
  }

  /**
   * The DSW items rendered under the create menu's "DMP Import" divider.
   *
   * Each item's accessible name is "<alias> DSW / FAIR Wizard", so this relies
   * on Playwright's default substring matching rather than an exact name.
   */
  dswImportMenuItems(): Locator {
    return this.page.getByRole("menuitem", { name: "DSW / FAIR Wizard" });
  }

  mountedCreateMenu(): Locator {
    return this.page.getByRole("menu", { name: "Create", exact: true, includeHidden: true });
  }

  async openCreateMenu(): Promise<void> {
    await this.sidebar.createButton.click();
    await this.page.getByRole("menu", { name: "Create", exact: true }).waitFor({ state: "visible" });
  }

  /** Requires the create menu to already be open. */
  async clickDSWImport(alias: string): Promise<DSWImportDialogComponent> {
    await this.page.getByRole("menuitem", { name: `${alias} DSW / FAIR Wizard` }).click();
    const dialog = new DSWImportDialogComponent(this.page);
    await dialog.waitForOpen();
    return dialog;
  }

  async openDSWImport(alias: string): Promise<DSWImportDialogComponent> {
    await this.openCreateMenu();
    return this.clickDSWImport(alias);
  }
}
