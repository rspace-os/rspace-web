import { expect } from "@playwright/experimental-ct-react";
import type { Locator } from "playwright-core";

export const isFullyInViewport = (element: Element) => {
  const rect = element.getBoundingClientRect();
  return (
    rect.top >= 0 &&
    rect.left >= 0 &&
    rect.bottom <= window.innerHeight &&
    rect.right <= window.innerWidth
  );
};

export const moveToastStackIntoViewport = (element: Element) => {
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
) => {
  await expect(locator).toBeVisible();
  if (options?.beforeWaiting) {
    await locator.evaluate(options.beforeWaiting);
  }
  await locator.scrollIntoViewIfNeeded();
  await expect
    .poll(async () => locator.evaluate(isFullyInViewport).catch(() => false), {
      timeout: options?.timeout,
    })
    .toBe(true);
  await locator.click();
};

