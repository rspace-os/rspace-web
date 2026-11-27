import type { Locator } from "@playwright/test";

/*
 * Playwright's allInnerTexts(), allTextContents(), evaluate() and dispatchEvent() resolve the
 * locator in the page's own JavaScript world. Since Playwright 1.65, role and label locators read
 * element.ariaLabelledByElements there and in Playwright's isolated world, which crashes Firefox 159
 * (microsoft/playwright#43967). These helpers read each match through innerText() and textContent(),
 * which stay in the isolated world.
 */

/** The innerText of every element the locator matches, in document order. */
export async function innerTexts(locator: Locator): Promise<string[]> {
  return Promise.all((await locator.all()).map((element) => element.innerText()));
}

/** The textContent of every element the locator matches, in document order. */
export async function textContents(locator: Locator): Promise<string[]> {
  return Promise.all((await locator.all()).map(async (element) => (await element.textContent()) ?? ""));
}
