import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { TableListResponsivePage } from "./pageObjects/TableListResponsivePage";
import { NarrowCardGridStory, TableListResponsiveStory } from "./TableListResponsive.story";

afterEach(() => cleanup());

describe("TableList responsive presentations", () => {
  test("keeps a long one-line title and its actions inside a narrow card grid", async () => {
    render(<NarrowCardGridStory />);
    const tableList = new TableListResponsivePage();
    const card = tableList.card("An instrument with a long one-line location and descriptive name");
    await expect.element(card).toBeVisible();
    const gridBounds = tableList.cards.element().getBoundingClientRect();
    expect(card.element().getBoundingClientRect().right).toBeLessThanOrEqual(gridBounds.right);
    expect(card.getByRole("button", { name: "Open" }).element().getBoundingClientRect().right).toBeLessThanOrEqual(
      gridBounds.right,
    );
  });

  test("switches on container width and retains controls and table state", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1200, 900);

    try {
      render(<TableListResponsiveStory />);
      const tableList = new TableListResponsivePage();

      await expect.element(tableList.containerWidth).toHaveTextContent("900px");
      await expect.element(tableList.table).toBeVisible();
      await expect.element(tableList.cards).not.toBeVisible();
      await tableList.sortByTitle();
      await tableList.hideOwner();
      await userEvent.click(tableList.nextPage);
      await expect.element(tableList.pageStatus).toHaveTextContent("Page 2 of 3");

      await userEvent.click(tableList.widthToggle);
      await expect.element(tableList.containerWidth).toHaveTextContent("520px");
      await expect.element(tableList.table).not.toBeVisible();
      await expect.element(tableList.cards).toBeVisible();
      await expect.element(tableList.card("Metabolomics batch 07")).toBeVisible();
      await expect.element(tableList.card("Organoid culture optimization")).toBeVisible();
      await expect.element(tableList.card("Organoid culture optimization").getByText("Owner")).not.toBeInTheDocument();
      await expect.element(tableList.filtersButton).toBeVisible();
      await expect.element(tableList.sortingButton).toBeVisible();
      await expect.element(tableList.columnsButton).toBeVisible();

      await userEvent.fill(tableList.search, "Maya");
      await expect.element(tableList.pageStatus).toHaveTextContent("Page 1 of 1");
      await expect.element(tableList.card("CRISPR screen notes")).toBeVisible();
      await expect.element(tableList.card("Organoid culture optimization")).toBeVisible();

      await userEvent.click(tableList.widthToggle);
      await expect.element(tableList.table).toBeVisible();
      await expect.element(tableList.cards).not.toBeVisible();
      await expect.element(tableList.row("CRISPR screen notes")).toBeVisible();
      await expect.element(tableList.row("Organoid culture optimization")).toBeVisible();
      await expect.element(tableList.table.getByRole("columnheader", { name: "Owner" })).not.toBeInTheDocument();
      await expectNoAxeViolations();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });
});
