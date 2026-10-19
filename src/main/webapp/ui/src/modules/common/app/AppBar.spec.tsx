import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { withViewport } from "@/__tests__/pageObjects/viewport";
import { AppBarStory } from "./AppBar.story";
import { AppBarPage } from "./pageObjects/AppBarPage";

const appBar = new AppBarPage();

afterEach(() => {
  cleanup();
});

describe("AppBar", () => {
  test("stays at the top of the viewport while the page scrolls", async () => {
    render(<AppBarStory />);
    await expect.element(appBar.header).toBeVisible();
    const top = appBar.headerTop();
    const originalScrollY = window.scrollY;

    try {
      await appBar.scrollDocumentTo(200);

      await expect.poll(() => window.scrollY).toBeGreaterThan(0);
      await expect.poll(() => appBar.headerTop()).toBe(top);
    } finally {
      await appBar.scrollDocumentTo(originalScrollY);
    }
  });

  test("keeps every header control inside a 320 CSS pixel viewport", async () => {
    await withViewport(320, async () => {
      render(<AppBarStory />);

      await expect.element(appBar.header).toBeVisible();
      await expect.element(appBar.help).toBeVisible();
      await expect.poll(() => appBar.documentFitsViewport()).toBe(true);
      await expect.poll(() => appBar.visibleControlsFitViewport()).toBe(true);
    });
  });
});
