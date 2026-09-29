import type { Locator, Page } from "@playwright/test";

export type GallerySection =
  | "Images"
  | "Audio"
  | "Videos"
  | "Documents"
  | "Chemistry"
  | "Miscellaneous"
  | "Snippets"
  | "Filestores"
  | "DMPs"
  | "Exports";

export class GallerySidebar {
  readonly root: Locator;
  readonly createButton: Locator;
  private readonly createMenu: Locator;
  private readonly openSidebarButton: Locator;
  private temporary = false;

  constructor(page: Page) {
    this.root = page.getByRole("region", { name: "gallery sections drawer" });
    this.createButton = this.root.getByRole("button", { name: "Create", exact: true });
    this.createMenu = page.getByRole("menu", { name: "Create", exact: true });
    this.openSidebarButton = page.getByRole("button", { name: "open sidebar" });
  }

  async ensureOpen(): Promise<void> {
    await this.root.or(this.openSidebarButton).first().waitFor({ state: "visible" });
    if (await this.openSidebarButton.isVisible()) {
      await this.openSidebarButton.click();
      await this.root.waitFor({ state: "visible" });
      this.temporary = true;
    }
  }

  async openSection(section: GallerySection): Promise<void> {
    await this.ensureOpen();
    await this.sectionTab(section).click();
    await this.waitUntilDismissed();
  }

  /**
   * The temporary drawer closes itself after a section change or a completed Create action.
   * Reopening it mid-animation would find it still open and click a tab about to unmount.
   */
  async waitUntilDismissed(): Promise<void> {
    if (this.temporary) await this.root.waitFor({ state: "hidden" });
  }

  /** DrawerTab exposes its selected state only as MUI's Mui-selected class, with no ARIA equivalent. */
  async isSelected(section: GallerySection): Promise<boolean> {
    await this.ensureOpen();
    return this.sectionTab(section).evaluate((tab) => tab.classList.contains("Mui-selected"));
  }

  private sectionTab(section: GallerySection): Locator {
    return this.root.getByRole("button", { name: section, exact: true });
  }

  async clickCreate(): Promise<void> {
    await this.ensureOpen();
    await this.createButton.click();
    await this.createMenu.waitFor({ state: "visible" });
  }
}
