import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { ContainerPageGrid, MoveDestinationGrid, SelectionOnlyGrid } from "./ContentGrid.story";
import { ContentGridPage } from "./pageObjects/ContentGridPage";

/*
 * Browser mode: jsdom does not model focus moving between the grid's roving
 * tab stops, which these keyboard flows depend on.
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

    await grid.press("{ArrowLeft} ");
    await expect.element(grid.announcement).toBeInTheDocument();
    expect(grid.announcementWidth()).toBeLessThanOrEqual(1);
    // axe measures the tips' contrast mid-fade otherwise.
    await expect.poll(() => grid.tipsOpacity()).toBe("1");
    await expectNoAxeViolations();
  });

  test("on the container page, the arrows still move the selection and Shift+Arrow selects a range", async () => {
    render(<ContainerPageGrid />);
    const grid = new ContentGridPage();
    await expect.element(grid.grid).toBeInTheDocument();

    await grid.tabIntoGrid();
    await grid.press("{Shift>}{ArrowRight}{/Shift}");
    await expect.element(grid.cell(0)).toHaveAttribute("aria-selected", "true");
    await expect.element(grid.cell(1)).toHaveAttribute("aria-selected", "true");

    await grid.press("{ArrowDown}");
    await expect.element(grid.cell(3)).toHaveAttribute("aria-selected", "true");
    await expect.element(grid.cell(0)).toHaveAttribute("aria-selected", "false");
    await expect.element(grid.cell(1)).toHaveAttribute("aria-selected", "false");
  });

  test("in the Move dialog, deselecting a location with Space keeps the keyboard focus in the grid", async () => {
    render(<MoveDestinationGrid />);
    const grid = new ContentGridPage();
    await expect.element(grid.grid).toBeInTheDocument();

    await grid.tabIntoGrid();
    await grid.press("  {ArrowRight} ");
    await expect.element(grid.cell(0)).toHaveAttribute("aria-selected", "false");
    await expect.element(grid.cell(1)).toHaveAttribute("aria-selected", "true");
    await expect.poll(() => grid.cell(1).element().contains(document.activeElement)).toBe(true);
  });
});
