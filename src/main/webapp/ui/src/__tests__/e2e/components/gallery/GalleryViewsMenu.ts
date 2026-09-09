import type { Locator, Page } from "@playwright/test";
import { galleryEmptyStateLocator } from "@/__tests__/e2e/components/gallery/GalleryEmptyState";

export type GalleryViewType = "Grid" | "Tree" | "Carousel";

export class GalleryViewsMenu {
  readonly button: Locator;
  readonly menu: Locator;

  constructor(private readonly page: Page) {
    this.button = page.getByRole("button", { name: "Views" });
    this.menu = page.getByRole("menu", { name: "view options" });
  }

  async switchTo(view: GalleryViewType): Promise<void> {
    await this.button.click();
    await this.menu.getByRole("menuitem", { name: view }).click();
    const target =
      view === "Grid"
        ? this.page.getByRole("grid", { name: "grid view of files" })
        : view === "Tree"
          ? this.page.getByRole("tree")
          : this.page.getByRole("region", { name: "Carousel view of files" });
    await target.waitFor({ state: "visible" });
  }

  /**
   * Used by GalleryPage.isLoaded()'s defensive fallback: switching to Grid view
   * of an empty folder never renders a grid.
   */
  async switchToGridOrEmpty(): Promise<void> {
    await this.button.click();
    await this.menu.getByRole("menuitem", { name: "Grid" }).click();
    const emptyState = galleryEmptyStateLocator(this.page);
    await this.page
      .getByRole("grid", { name: "grid view of files" })
      .or(emptyState)
      .first()
      .waitFor({ state: "visible" });
  }
}
