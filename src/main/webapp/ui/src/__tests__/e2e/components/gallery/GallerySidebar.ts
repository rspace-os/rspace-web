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
  private readonly openSidebarButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("region", { name: "gallery sections drawer" });
    this.createButton = this.root.getByRole("button", { name: "Create", exact: true });
    this.openSidebarButton = page.getByRole("button", { name: "open sidebar" });
  }

  async ensureOpen(): Promise<void> {
    await this.root.or(this.openSidebarButton).first().waitFor({ state: "visible" });
    if (await this.openSidebarButton.isVisible()) {
      await this.openSidebarButton.click();
      await this.root.waitFor({ state: "visible" });
    }
  }

  async openSection(section: GallerySection): Promise<void> {
    await this.ensureOpen();
    await this.root.getByRole("button", { name: section, exact: true }).click();
  }
}
