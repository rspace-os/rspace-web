import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { SimpleExample } from "./useOneDimensionalRovingTabIndex.story";

const feature = test.extend<{
  Given: {
    "the simple example component is rendered": () => Promise<void>;
  };
  Once: Record<string, never>;
  When: {
    "the user presses the tab key {count} times": ({
      count,
    }: {
      count: number;
    }) => Promise<void>;
    "the roving list has focus": () => Promise<void>;
    "the user presses the down arrow key": () => Promise<void>;
  };
  Then: {
    "there should be a button": () => Promise<void>;
    "the before button should gain focus": () => Promise<void>;
    "the after button should gain focus": () => Promise<void>;
    "the second list item gains focus": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the simple example component is rendered": async () => {
        await mount(<SimpleExample />);
      },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({
      "the user presses the tab key {count} times": async ({
        count,
      }: {
        count: number;
      }) => {
        for (let i = 0; i < count; i++) {
          await page.keyboard.press("Tab");
        }
      },
      "the roving list has focus": async () => {
        let elementHasFocus = false;
        while (!elementHasFocus) {
          await page.keyboard.press("Tab");
          elementHasFocus =
            (await page.evaluate(
              () =>
                (
                  document.activeElement?.parentNode as HTMLElement | null
                )?.tagName.toLowerCase() === "li"
            )) ?? false;
        }
      },
      "the user presses the down arrow key": async () => {
        await page.keyboard.press("ArrowDown");
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "there should be a button": async () => {
        await expect(page.getByRole("button")).toBeVisible();
      },
      "the before button should gain focus": async () => {
        const firstButton = page.getByRole("button").first();
        await expect(firstButton).toBeFocused();
      },
      "the after button should gain focus": async () => {
        const secondButton = page.getByRole("button").last();
        await expect(secondButton).toBeFocused();
      },
      "the second list item gains focus": async () => {
        const secondListItem = page
          .getByRole("listitem")
          .nth(1)
          .getByRole("button");
        await expect(secondListItem).toBeFocused();
      },
    });
  },
});

test.describe("useOneDimensionalRovingTabIndex", () => {
  feature("Tab focuses the before button", async ({ Given, When, Then }) => {
    await Given["the simple example component is rendered"]();
    await When["the user presses the tab key {count} times"]({ count: 1 });
    await Then["the before button should gain focus"]();
  });
  feature(
    "Pressing tab thrice focusses the after button",
    async ({ Given, When, Then }) => {
      await Given["the simple example component is rendered"]();
      await When["the user presses the tab key {count} times"]({ count: 3 });
      await Then["the after button should gain focus"]();
    }
  );
  test.describe("The arrow keys traverse the roving list", () => {
    feature("The down arrow moves the focus", async ({ Given, When, Then }) => {
      await Given["the simple example component is rendered"]();
      await When["the roving list has focus"]();
      await When["the user presses the down arrow key"]();
      await Then["the second list item gains focus"]();
    });
  });
});
