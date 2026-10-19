import type { Locator, Page } from "@playwright/test";

export function signedStatusLocator(page: Page): Locator {
  return page.locator(
    "#signedStatus:visible, #signedAwaitingWitnessStatus:visible, " +
      "#signedWitnessesDeclinedStatus:visible, #witnessedStatus:visible",
  );
}
