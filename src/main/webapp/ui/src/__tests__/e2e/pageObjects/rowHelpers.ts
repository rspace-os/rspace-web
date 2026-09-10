import type { Locator } from "@playwright/test";

/**
 * Finds rows containing a link with the exact accessible name.
 * Scope container to the target table; duplicate names match multiple rows.
 */
export function rowWithLink(container: Locator, name: string): Locator {
  return container.getByRole("row").filter({
    has: container.page().getByRole("link", {
      name,
      exact: true,
    }),
  });
}
