import type { Locator, Page } from "@playwright/test";

/**
 * A table/tree row identified by an exact-named link inside it (e.g. a record or form
 * name). Shared by page objects whose rows are keyed this same way
 */
export function rowWithLink(scope: Locator | Page, page: Page, name: string): Locator {
  return scope.getByRole("row").filter({ has: page.getByRole("link", { name, exact: true }) });
}
