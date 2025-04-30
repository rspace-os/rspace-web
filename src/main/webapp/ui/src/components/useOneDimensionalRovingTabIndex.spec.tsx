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
  };
  Then: {
    "there should be a button": () => Promise<void>;
    "the before button should gain focus": () => Promise<void>;
    "the after button should gain focus": () => Promise<void>;
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
});
