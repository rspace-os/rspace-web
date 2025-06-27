import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { ImportDialogStory } from "./ImportDialog.story";

const feature = test.extend<{
  Given: {
    "that the ImportDialog is mounted": () => Promise<void>;
  };
  Once: Record<string, never>;
  When: Record<string, never>;
  Then: {
    "there should be a dialog visible": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "that the ImportDialog is mounted": async () => {
        await mount(<ImportDialogStory />);
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
      "there should be a dialog visible": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({}) => {});

feature.afterEach(({}) => {});

test.describe("ImportDialog", () => {
  feature("Renders correctly", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a dialog visible"]();
  });
});
