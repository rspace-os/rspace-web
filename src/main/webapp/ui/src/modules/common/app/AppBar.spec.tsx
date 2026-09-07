import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { page } from "vitest/browser";
import { AppBarStory } from "./AppBar.story";
import { AppBarPage } from "./pageObjects/AppBarPage";

vi.mock("./AccountMenu", () => ({
  default: () => <button type="button" aria-label="Account Menu" className="size-8 shrink-0" />,
}));

const appBar = new AppBarPage();

afterEach(() => {
  cleanup();
});

describe("AppBar", () => {
  test("keeps every header control inside a 320 CSS pixel viewport", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(320, 900);

    try {
      render(<AppBarStory />);

      await expect.element(appBar.header).toBeVisible();
      await expect.element(appBar.help).toBeVisible();
      await expect.poll(() => appBar.documentFitsViewport()).toBe(true);
      await expect.poll(() => appBar.isFullyWithinViewport(appBar.help)).toBe(true);
      await expect.poll(() => appBar.visibleControlsFitViewport()).toBe(true);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });
});
