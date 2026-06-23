import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { RovingTabIndexPage } from "./pageObjects/RovingTabIndexPage";
import { HorizontalExample, SimpleExample } from "./useOneDimensionalRovingTabIndex.story";

const rovingPage = new RovingTabIndexPage();

afterEach(cleanup);

describe("useOneDimensionalRovingTabIndex", () => {
  test("The before button is focusable", async () => {
    render(<SimpleExample />);
    await rovingPage.focusBeforeButton();
    await expect.element(rovingPage.beforeButton).toHaveFocus();
  });

  test("Tabbing through the roving list focusses the after button", async () => {
    render(<SimpleExample />);
    await rovingPage.focusBeforeButton();
    await rovingPage.pressTabMultipleTimes(2);
    await expect.element(rovingPage.lastButton).toHaveFocus();
  });

  describe("The arrow keys traverse the roving list", () => {
    test("The down arrow moves the focus", async () => {
      render(<SimpleExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowDown();
      await expect.element(rovingPage.secondListItemButton).toHaveFocus();
    });

    test("The up arrow moves the focus back", async () => {
      render(<SimpleExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowDown();
      await rovingPage.pressArrowUp();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });

    test("The focus wraps back to the beginning", async () => {
      render(<SimpleExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowDown();
      await rovingPage.pressArrowDown();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });

    test("The focus wraps forward to the end", async () => {
      render(<SimpleExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowUp();
      await rovingPage.pressArrowUp();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });

    test("The right arrow moves the focus in the horizontal layout", async () => {
      render(<HorizontalExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowRight();
      await expect.element(rovingPage.secondListItemButton).toHaveFocus();
    });

    test("The left arrow moves the focus back in the horizontal layout", async () => {
      render(<HorizontalExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowRight();
      await rovingPage.pressArrowLeft();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });

    test("The focus wraps back to the beginning in horizontal layout", async () => {
      render(<HorizontalExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowRight();
      await rovingPage.pressArrowRight();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });

    test("The focus wraps forward to the end in horizontal layout", async () => {
      render(<HorizontalExample />);
      await rovingPage.focusFirstListItem();
      await rovingPage.pressArrowLeft();
      await rovingPage.pressArrowLeft();
      await expect.element(rovingPage.firstListItemButton).toHaveFocus();
    });
  });

  test("Leaving the roving list preserves the last focussed tab stop", async () => {
    render(<SimpleExample />);
    await rovingPage.focusFirstListItem();
    await rovingPage.pressArrowDown();
    await rovingPage.shiftTab();
    await expect.element(rovingPage.beforeButton).toHaveFocus();
    await expect.element(rovingPage.firstListItemButton).toHaveAttribute("tabindex", "-1");
    await expect.element(rovingPage.secondListItemButton).toHaveAttribute("tabindex", "0");
  });
});
