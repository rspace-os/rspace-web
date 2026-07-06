import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { server } from "vitest/browser";
import { emulateReducedMotion } from "../__tests__/pageObjects/accessibility";
import { SkipToContentMenuPage } from "./pageObjects/SkipToContentMenuPage";
import { DynamicLandmarksExample, SimpleTestExample } from "./SkipToContentMenu.story";

const menu = new SkipToContentMenuPage();

afterEach(cleanup);

describe("SkipToContentMenu", () => {
  test("skip button becomes visible when focused", async () => {
    render(<SimpleTestExample />);
    menu.focusSkipButton();
    await expect.element(menu.skipButton("Header")).toBeVisible();
    await expect.element(menu.skipButton("Footer")).toBeVisible();
  });

  test("skip button allows navigation to a landmark", async () => {
    render(<SimpleTestExample />);
    menu.focusSkipButton();
    await menu.activateSkipButton("Header");
    await expect.element(menu.landmarkContent("Header")).toHaveFocus();
  });

  test("arrow keys navigate through the landmark list", async () => {
    render(<SimpleTestExample />);
    menu.focusSkipButton();
    await expect.element(menu.skipButton("Header")).toBeVisible();
    await menu.pressArrowDown();
    await expect.element(menu.skipButton("Footer")).toHaveFocus();
    await menu.pressArrowUp();
    await expect.element(menu.skipButton("Header")).toHaveFocus();
  });

  test("dynamic landmark registration updates the list", async () => {
    render(<DynamicLandmarksExample />);
    await menu.showExtraLandmarks();
    menu.focusSkipButton();
    await expect.element(menu.skipButton("Sidebar")).toBeVisible();
    await expect.element(menu.skipButton("Comments")).toBeVisible();
  });

  test("escape key closes the skip-to-content menu", async () => {
    render(<SimpleTestExample />);
    menu.focusSkipButton();
    await expect.element(menu.skipButton("Header")).toBeVisible();
    await menu.pressEscape();
    // The component hides via CSS opacity transition (0.2s). After Escape the
    // isVisible state is set to false, which drives opacity to 0 on the outer
    // container. Wait for the transition to complete then verify opacity is 0.
    await expect.poll(() => getComputedStyle(menu.containerElement()).opacity, { timeout: 1000 }).toBe("0");
  });

  test("disables the show/hide transition when prefers-reduced-motion is set", async () => {
    await emulateReducedMotion();
    render(<SimpleTestExample />);
    menu.focusSkipButton();
    await expect.element(menu.skipButton("Header")).toBeVisible();

    // CDP media emulation is chromium-only; on other engines emulateReducedMotion
    // is a no-op so the transition assertion would not be meaningful.
    if (server.browser === "chromium") {
      expect(getComputedStyle(menu.containerElement()).transitionDuration).toBe("0s");
    }
  });
});
