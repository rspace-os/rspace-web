import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NewNoteStory } from "./NewNote.story";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "that the new note field has been mounted": () => Promise<void>;
  };
  Once: {};
  When: {};
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "that the new note field has been mounted": async () => {
        await mount(<NewNoteStory />);
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({});
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            /*
             * These violations are expected in component tests as we're not rendering
             * a complete page with proper document structure:
             *
             * 1. MUI DataGrid renders its immediate children with role=presentation,
             *    which Firefox considers to be a violation
             * 2. Component tests don't have main landmarks as they're isolated components
             * 3. Component tests typically don't have h1 headings as they're not full pages
             * 4. Content not in landmarks is expected in component testing context
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          }),
        ).toEqual([]);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({}) => {});

feature.afterEach(({}) => {});

test.describe("NewNote", () => {
  feature("Has no accessiblity violations", async ({ Given, Then }) => {
    await Given["that the new note field has been mounted"]();
    await Then["there shouldn't be any axe violations"]();
  });
});
