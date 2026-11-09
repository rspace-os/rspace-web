import { type Locator, page } from "vitest/browser";

// These table/card wrappers have no useful accessible query handle.
export function css(selector: string): Locator {
  const root = page.elementLocator(document.body) as unknown as { locator: (value: string) => Locator };
  return root.locator(`css=${selector}`);
}

export function bookableItemHeading(name: string, level?: 1 | 2 | 3 | 4 | 5 | 6): Locator {
  return page.getByRole("heading", level === undefined ? { name } : { name, level });
}

export function bookableItemTarget(globalId: string): Locator {
  return page.getByText(globalId, { exact: true });
}
