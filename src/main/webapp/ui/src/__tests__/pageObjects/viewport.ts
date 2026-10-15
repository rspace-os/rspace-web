/**
 * Browser-mode port of src/__tests__/playwright/viewport.ts.
 *
 * `isFullyInViewport` and `moveToastStackIntoViewport` are identical plain-DOM
 * functions. `clickWhenInViewport` is rewritten against the `vitest/browser`
 * Locator API (no Playwright dependency) so it can run inside the Vitest
 * browser runner.
 */
import { expect } from "vitest";
import { type Locator, page } from "vitest/browser";

export async function withViewport<T>(width: number, callback: () => Promise<T>): Promise<T> {
  const originalViewport = { width: window.innerWidth, height: window.innerHeight };
  try {
    await page.viewport(width, 900);
    return await callback();
  } finally {
    await page.viewport(originalViewport.width, originalViewport.height);
  }
}

const isFullyInViewport = (element: Element): boolean => {
  const rect = element.getBoundingClientRect();
  return rect.top >= 0 && rect.left >= 0 && rect.bottom <= window.innerHeight && rect.right <= window.innerWidth;
};

export const moveToastStackIntoViewport = (element: Element): void => {
  const toasts = element.ownerDocument.querySelector('[data-testid="Toasts"]');
  if (toasts instanceof HTMLElement) {
    toasts.style.top = "128px";
  }
};

export const clickWhenInViewport = async (
  locator: Locator,
  options?: {
    beforeWaiting?: (element: Element) => void;
    timeout?: number;
  },
): Promise<void> => {
  await expect.element(locator).toBeVisible();
  if (options?.beforeWaiting) {
    options.beforeWaiting(locator.element());
  }
  locator.element().scrollIntoView();
  await expect
    .poll(() => isFullyInViewport(locator.element()), {
      timeout: options?.timeout,
    })
    .toBe(true);
  await locator.click();
};
