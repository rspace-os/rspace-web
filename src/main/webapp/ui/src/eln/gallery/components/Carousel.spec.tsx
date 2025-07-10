import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { SimpleCarousel } from "./Carousel.story";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the carousel is shown": () => Promise<void>;
  };
  When: {
    "the user clicks the next button": () => Promise<void>;
    "the user zooms in on the image": () => Promise<void>;
  };
  Then: {
    "there shouldn't be any axe violations": () => Promise<void>;
    "the progress indicator should read": (text: string) => Promise<void>;
    "the zoom level should be the initial value": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the carousel is shown": async () => {
        await mount(<SimpleCarousel />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks the next button": async () => {
        await page.getByRole("button", { name: /next/i }).click();
      },
      "the user zooms in on the image": async () => {
        await page.getByRole("button", { name: /zoom in/i }).click();
      },
    });
  },
  Then: async ({ page }, use) => {
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
             * 1. Component tests don't have main landmarks as they're isolated components
             * 2. Component tests typically don't have h1 headings as they're not full pages
             * 3. Content not in landmarks is expected in component testing context
             */
            return (
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          })
        ).toEqual([]);
      },
      "the progress indicator should read": async (text: string) => {
        await expect(
          page.getByRole("status", { name: "Current file index" })
        ).toHaveText(text);
      },
      "the zoom level should be the initial value": async () => {
        await expect(
          page.getByRole("button", { name: /reset zoom/i })
        ).toBeDisabled();
      },
    });
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/deploymentproperties/ajax/property*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
    });
  });
  await router.route("/*/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });
  await router.route("/gallery/getThumbnail/*/*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/png",
      body: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8//8/AwAI/wH+9Q4AAAAASUVORK5CYII=",
        "base64"
      ),
    });
  });
  await router.route("/api/v1/files/*/*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/png",
      body: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8//8/AwAI/wH+9Q4AAAAASUVORK5CYII=",
        "base64"
      ),
    });
  });
});

feature.afterEach(({}) => {});

test.describe("Carousel", () => {
  feature("Should have no axe violations", async ({ Given, Then }) => {
    await Given["the carousel is shown"]();
    await Then["there shouldn't be any axe violations"]();
  });

  feature(
    "Should show an indicator of progress through listing.",
    async ({ Given, When, Then }) => {
      await Given["the carousel is shown"]();
      await Then["the progress indicator should read"]("1 / 8");
      await When["the user clicks the next button"]();
      await Then["the progress indicator should read"]("2 / 8");
    }
  );

  feature(
    "Moving to a different file resets the zoom level",
    async ({ Given, When, Then }) => {
      await Given["the carousel is shown"]();
      await When["the user zooms in on the image"]();
      await When["the user clicks the next button"]();
      await Then["the zoom level should be the initial value"]();
    }
  );
});
