import { type Locator, page } from "vitest/browser";
import { bookableItemHeading, bookableItemTarget } from "../../bookable-items/pageObjects/bookableItemLocators";
export class AllBookableItemsPage {
  readonly instrumentName = page.getByRole("link", { name: "Confocal microscope", exact: true }).first();
  readonly bookableItemDetailsHeading = bookableItemHeading("Confocal microscope");
  readonly bookableItemDetailsTarget = bookableItemTarget("IN123");
  readonly heading = page.getByRole("heading", { name: "All items" });
  readonly table = page.getByRole("table");
  get detailsButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Confocal microscope" })
      .getByRole("link", { name: "View details", exact: true });
  }
  get electronBookButton(): Locator {
    return this.table
      .getByRole("row")
      .filter({ hasText: "Electron microscope" })
      .getByRole("link", { name: "Book", exact: true });
  }
}
