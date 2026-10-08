import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { SelectionOnlyGrid } from "./ContentGrid.story";
import { ContentGridPage } from "./pageObjects/ContentGridPage";

/*
 * Browser mode: jsdom does not model focus moving between the grid's roving
 * tab stops, which this keyboard flow depends on.
 */
describe("ContentGrid", () => {
  afterEach(cleanup);

  test("in a selection-only grid, arrows move focus and Space selects locations that are not next to each other", async () => {
    render(<SelectionOnlyGrid />);
    const grid = new ContentGridPage();
    await expect.element(grid.grid).toBeInTheDocument();

    await grid.tabIntoGrid();
    await grid.press(" {ArrowRight}{ArrowDown}");
    await expect.element(grid.cell(0)).toHaveAttribute("aria-selected", "true");
    await expect.element(grid.cell(1)).toHaveAttribute("aria-selected", "false");
    await expect.poll(() => grid.cell(3).element().contains(document.activeElement)).toBe(true);
    expect(grid.focusIndicator(3)).not.toBe("none");
    expect(grid.focusIndicator(1)).toBe("none");

    await grid.press(" ");
    await expect.element(grid.cell(3)).toHaveAttribute("aria-selected", "true");
    await expect.element(grid.cell(2)).toHaveAttribute("aria-selected", "false");
  });
});
