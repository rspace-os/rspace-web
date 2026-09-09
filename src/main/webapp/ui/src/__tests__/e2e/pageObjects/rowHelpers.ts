import type { Locator, Page } from "@playwright/test";

/**
 * A row keyed by an exact-named link inside it (record, form, whatever). As
 * unique as the name you pass: it returns every match, so callers expecting
 * one result need a genuinely unique name (`uniqueName()`); callers expecting
 * several (e.g. after duplicating a record) should assert the count instead.
 */
export function rowWithLink(scope: Locator | Page, name: string): Locator {
  // Locators know their Page via `.page()`; Page itself doesn't, which is how we tell them apart.
  const page = "page" in scope ? scope.page() : scope;
  return scope.getByRole("row").filter({ has: page.getByRole("link", { name, exact: true }) });
}
