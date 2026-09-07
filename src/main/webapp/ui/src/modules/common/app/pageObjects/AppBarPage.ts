import { type Locator, page } from "vitest/browser";

export class AppBarPage {
  readonly header: Locator = page.getByRole("banner");
  readonly help: Locator = page.getByRole("button", { name: "Open Help" });

  documentFitsViewport(): boolean {
    return document.documentElement.scrollWidth <= document.documentElement.clientWidth;
  }

  isFullyWithinViewport(locator: Locator): boolean {
    const bounds = locator.element().getBoundingClientRect();
    return bounds.left >= 0 && bounds.right <= window.innerWidth;
  }

  visibleControlsFitViewport(): boolean {
    return Array.from(this.header.element().querySelectorAll("button, a"))
      .filter((element) => element.getClientRects().length > 0)
      .every((element) => {
        const bounds = element.getBoundingClientRect();
        return bounds.left >= 0 && bounds.right <= window.innerWidth;
      });
  }
}
