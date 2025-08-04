import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { SimpleTestExample, DynamicLandmarksExample } from "./SkipToContentButton.story";
import { type emptyObject } from "../util/types";

const feature = test.extend<{
  Given: {
    "a simple skip-to-content component is rendered": () => Promise<void>;
    "a dynamic landmarks component is rendered": () => Promise<void>;
  };
  Once: emptyObject;
  When: {
    "the user focuses the skip button": () => Promise<void>;
    "the user clicks on a landmark option": (params: { landmarkName: string }) => Promise<void>;
    "the user presses tab": () => Promise<void>;
    "the user adds extra landmarks": () => Promise<void>;
  };
  Then: {
    "the skip button should be visible": () => Promise<void>;
    "the skip button should be hidden": () => Promise<void>;
    "the landmark options should be displayed": () => Promise<void>;
    "the selected landmark should be focused": (params: { landmarkName: string }) => Promise<void>;
    "the landmark list should include the new landmarks": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "a simple skip-to-content component is rendered": async () => {
        await mount(<SimpleTestExample />);
      },
      "a dynamic landmarks component is rendered": async () => {
        await mount(<DynamicLandmarksExample />);
      },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({
      "the user focuses the skip button": async () => {
        await page.focus('button:has-text("Skip to content")');
      },
      "the user clicks on a landmark option": async ({ landmarkName }: { landmarkName: string }) => {
        await page.click(`text=Skip to ${landmarkName}`);
      },
      "the user presses tab": async () => {
        await page.keyboard.press("Tab");
      },
      "the user adds extra landmarks": async () => {
        await page.click('button:has-text("Show Extra Landmarks")');
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the skip button should be visible": async () => {
        await expect(page.getByText("Skip to content")).toBeVisible();
      },
      "the skip button should be hidden": async () => {
        await expect(page.getByText("Skip to content")).not.toBeVisible();
      },
      "the landmark options should be displayed": async () => {
        await expect(page.getByText("Skip to Header")).toBeVisible();
        await expect(page.getByText("Skip to Footer")).toBeVisible();
      },
      "the selected landmark should be focused": async ({ landmarkName }: { landmarkName: string }) => {
        await expect(page.getByText(`${landmarkName} Content`)).toBeFocused();
      },
      "the landmark list should include the new landmarks": async () => {
        await expect(page.getByText("Skip to Sidebar")).toBeVisible();
        await expect(page.getByText("Skip to Comments")).toBeVisible();
      },
    });
  },
});

test.describe("SkipToContentButton", () => {
  feature("Skip button becomes visible when focused", async ({ Given, When, Then }) => {
    await Given["a simple skip-to-content component is rendered"]();
    await When["the user focuses the skip button"]();
    await Then["the skip button should be visible"]();
    await Then["the landmark options should be displayed"]();
  });

  feature("Skip button allows navigation to landmarks", async ({ Given, When, Then }) => {
    await Given["a simple skip-to-content component is rendered"]();
    await When["the user focuses the skip button"]();
    await When["the user clicks on a landmark option"]({ landmarkName: "Header" });
    await Then["the selected landmark should be focused"]({ landmarkName: "Header" });
  });

  feature("Dynamic landmark registration updates the list", async ({ Given, When, Then }) => {
    await Given["a dynamic landmarks component is rendered"]();
    await When["the user adds extra landmarks"]();
    await When["the user focuses the skip button"]();
    await Then["the landmark list should include the new landmarks"]();
  });
});
