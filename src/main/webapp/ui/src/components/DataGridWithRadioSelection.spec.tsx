import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { DataGridWithFeatures, DataGridWithRadioSelectionExample } from "./DataGridWithRadioSelection.story";
import { DataGridWithRadioSelectionPage } from "./pageObjects/DataGridWithRadioSelectionPage";

/*
 * Browser-bound cases ported from DataGridWithRadioSelection.spec.tsx
 * (Playwright CT) to Vitest Browser Mode.
 *
 * These tests stay in the browser runner (not jsdom) because they exercise
 * behaviour the vitest MUI DataGrid stub cannot reproduce:
 *   - real Tab focus traversal across the grid (keyboard tests),
 *   - MUI's actual sorting / pagination / page-size behaviour, and
 *   - MUI's real column layout where the radio is genuinely the first column.
 */

const grid = new DataGridWithRadioSelectionPage();

afterEach(cleanup);

describe("DataGridWithRadioSelection", () => {
  describe("Basic Rendering and Initial State", () => {
    test("radio selection column is the first column", async () => {
      render(<DataGridWithRadioSelectionExample />);
      await expect.element(grid.grid).toBeVisible();

      await expect.element(grid.firstColumnHeader).toHaveTextContent("Select");

      // Every data row's first gridcell should contain a radio button
      const rows = grid.dataRows().elements();
      expect(rows.length).toBeGreaterThan(0);
      for (let i = 0; i < rows.length; i++) {
        await expect.element(grid.firstCellForRow(i).getByRole("radio")).toBeVisible();
      }
    });
  });

  describe("Keyboard Navigation and Accessibility", () => {
    test("user can tab to the radio button of the first row", async () => {
      render(<DataGridWithRadioSelectionExample />);
      await expect.element(grid.grid).toBeVisible();

      await grid.tabToRadioForRow(0);
      await expect.element(grid.radioForRow(0)).toHaveFocus();
    });

    test("user can select a row by tabbing to its radio and pressing Space", async () => {
      render(<DataGridWithRadioSelectionExample />);
      await expect.element(grid.grid).toBeVisible();

      await grid.tabToRadioForRow(0);
      await grid.pressSpace();

      await expect.element(grid.radioForRow(0)).toBeChecked();
      await expect.element(grid.dataRow(0)).toHaveAttribute("aria-selected", "true");
    });
  });

  describe("Integration with MUI DataGrid Features", () => {
    test("sorting moves the selected row to its new position", async () => {
      render(<DataGridWithFeatures />);
      await expect.element(grid.grid).toBeVisible();

      // Select the first row (John, age 35)
      await grid.clickRadioForRow(0);
      await expect.element(grid.radioForRow(0)).toBeChecked();
      await expect.element(grid.dataRow(0)).toHaveAttribute("aria-selected", "true");

      // Sort by age ascending — John (35) is now the youngest so he moves to row 0,
      // but the data set is: Jane 28, John 35, Alice 31 — after asc sort by age the
      // order is Jane (28), Alice (31), John (35), so John is at index 2.
      await grid.clickSort("age");

      // Selection should follow the row: John is now at index 2
      await expect.element(grid.radioForRow(2)).toBeChecked();
      await expect.element(grid.dataRow(2)).toHaveAttribute("aria-selected", "true");
    });

    test("selection is maintained in the data model across page navigation", async () => {
      render(<DataGridWithFeatures />);
      await expect.element(grid.grid).toBeVisible();

      // Select the first row (id=1)
      await grid.clickRadioForRow(0);
      await expect.element(grid.radioForRow(0)).toBeChecked();

      // Navigate to the next page — the selected row is no longer visible
      await grid.goToNextPage();
      await expect.element(grid.selectionIndicator).toHaveTextContent("Selected ID: 1");

      // Navigate back — the radio should be checked again
      await grid.goToPreviousPage();
      await expect.element(grid.radioForRow(0)).toBeChecked();
      await expect.element(grid.dataRow(0)).toHaveAttribute("aria-selected", "true");
    });

    test("selection is preserved when page size changes", async () => {
      render(<DataGridWithFeatures />);
      await expect.element(grid.grid).toBeVisible();

      // Select the third row (index 2)
      await grid.clickRadioForRow(2);
      await expect.element(grid.radioForRow(2)).toBeChecked();

      // Change page size from 3 to 5 — all rows should now be visible
      await grid.changePageSize(5);

      // The previously selected row should still be selected
      await expect.element(grid.radioForRow(2)).toBeChecked();
      await expect.element(grid.dataRow(2)).toHaveAttribute("aria-selected", "true");
    });
  });
});
