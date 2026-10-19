import type { Locator, Page } from "@playwright/test";

/** The "no items" message a gallery folder shows in every view (grid, tree, carousel) when empty. */
export function galleryEmptyStateLocator(scope: Page | Locator): Locator {
  return scope.getByText("There are no top-level");
}
