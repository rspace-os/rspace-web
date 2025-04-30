import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { UseOneDimensionalRovingTabIndexStory } from "./useOneDimensionalRovingTabIndex.story";

const feature = test.extend<{
  Given: {
    "the story is rendered": () => Promise<void>;
  };
  Once: Record<string, never>;
  When: Record<string, never>;
  Then: {
    "there should be a button": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the story is rendered": async () => {
        await mount(<UseOneDimensionalRovingTabIndexStory />);
      },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({}, use) => {
    await use({});
  },
  Then: async ({ page }, use) => {
    await use({
      "there should be a button": async () => {
        await expect(page.getByRole("button")).toBeVisible();
      },
    });
  },
});

test.describe("useOneDimensionalRovingTabIndex", () => {
  feature(
    "when the story is rendered, then there should be a button",
    async ({ Given, Then }) => {
      await Given["the story is rendered"]();
      await Then["there should be a button"]();
    }
  );
});
