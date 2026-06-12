import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  DataGridWithRadioSelectionExample,
  DataGridWithFeatures,
} from "./DataGridWithRadioSelection.story";

/*
 * Browser-bound cases only.
 *
 * The rest of this spec was converted to a jsdom Vitest test in
 * DataGridWithRadioSelection.test.tsx. The cases below stay in Playwright
 * because they exercise behaviour the vitest MUI DataGrid stub
 * (src/test-stubs/MuiDataGridStub.tsx) cannot reproduce:
 *   - real `Tab` focus traversal across the grid (the two keyboard tests),
 *   - MUI's actual sorting / pagination / page-size behaviour, and
 *   - MUI's real column layout, where the radio is genuinely the first column
 *     (the stub renders an extra native checkbox column alongside it).
 */
const feature = test.extend<{
  Given: {
    "the data grid with radio selection is rendered": () => Promise<void>;
    "the data grid with additional features is rendered": () => Promise<void>;
  };
  When: {
    "the user clicks on the radio button for row {i}": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the user tabs to the radio button for row {i}": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the user presses the space key": () => Promise<void>;
    "the user clicks the sort button for column {field}": ({
      field,
    }: {
      field: string;
    }) => Promise<void>;
    "the user changes the page size to {size}": ({
      size,
    }: {
      size: number;
    }) => Promise<void>;
    "the user navigates to the next page": () => Promise<void>;
    "the user navigates to the previous page": () => Promise<void>;
  };
  Then: {
    "the row {i} should be selected": ({ i }: { i: number }) => Promise<void>;
    "the selection indicator should show {id}": ({
      id,
    }: {
      id: number | null;
    }) => Promise<void>;
    "the radio button for row {i} should have focus": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the radio column should be the first column": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the data grid with radio selection is rendered": async () => {
        await mount(<DataGridWithRadioSelectionExample />);
      },
      "the data grid with additional features is rendered": async () => {
        await mount(<DataGridWithFeatures />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks on the radio button for row {i}": async ({ i }) => {
        await page
          .getByRole("row")
          .nth(i + 1)
          .getByRole("radio")
          .click();
      },
      "the user tabs to the radio button for row {i}": async ({ i }) => {
        // Repeatedly press Tab until the radio button for the specified row is focused
        let found = false;
        const maxTabs = 20; // Safety limit to prevent infinite loops

        let tabCount = 0;
        while (!found && tabCount < maxTabs) {
          await page.keyboard.press("Tab");
          found = await page
            .getByRole("row")
            .nth(i + 1)
            .getByRole("radio")
            .evaluate((radio) => document.activeElement === radio);
          tabCount++;
        }
      },
      "the user presses the space key": async () => {
        await page.keyboard.press("Space");
      },
      "the user clicks the sort button for column {field}": async ({
        field,
      }) => {
        await page.getByRole("columnheader", { name: field }).click();
      },
      "the user changes the page size to {size}": async ({ size }) => {
        await page.getByRole("combobox", { name: /Rows per page/i }).click();
        await page
          .getByRole("listbox")
          .getByRole("option", { name: `${size}` })
          .click();
      },
      "the user navigates to the next page": async () => {
        await page.getByRole("button", { name: /Go to next page/i }).click();
      },
      "the user navigates to the previous page": async () => {
        await page
          .getByRole("button", { name: /Go to previous page/i })
          .click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the row {i} should be selected": async ({ i }) => {
        await expect(
          page
            .getByRole("row")
            .nth(i + 1)
            .getByRole("radio")
        ).toBeChecked();
        await expect(page.getByRole("row").nth(i + 1)).toHaveAttribute(
          "aria-selected",
          "true"
        );
      },
      "the selection indicator should show {id}": async ({ id }) => {
        if (id === null) {
          await expect(page.getByTestId("selection-indicator")).toHaveText(
            "Nothing selected"
          );
        } else {
          await expect(page.getByTestId("selection-indicator")).toHaveText(
            `Selected ID: ${id}`
          );
        }
      },
      "the radio button for row {i} should have focus": async ({ i }) => {
        expect(
          await page
            .getByRole("row")
            .nth(i + 1)
            .getByRole("radio")
            .evaluate((radio) => document.activeElement === radio)
        ).toBe(true);
      },
      "the radio column should be the first column": async () => {
        await expect(page.getByRole("columnheader").first()).toHaveText(
          "Select"
        );
        const rows = await page.getByRole("row").all();
        // Skip the first row (header row)
        for (const row of rows.slice(1)) {
          await expect(
            row.getByRole("gridcell").first().getByRole("radio")
          ).toBeVisible();
        }
      },
    });
  },
});
test.describe("DataGridWithRadioSelection", () => {
  test.describe("Basic Rendering and Initial State", () => {
    feature(
      "Component renders with radio selection column as first column",
      async ({ Given, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await Then["the radio column should be the first column"]();
      }
    );
  });
  test.describe("Keyboard Navigation and Accessibility", () => {
    feature("Users can tab to radio buttons", async ({ Given, When, Then }) => {
      await Given["the data grid with radio selection is rendered"]();
      await When["the user tabs to the radio button for row {i}"]({ i: 0 });
      await Then["the radio button for row {i} should have focus"]({ i: 0 });
    });
    feature(
      "Users can select rows using keyboard",
      async ({ Given, When, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await When["the user tabs to the radio button for row {i}"]({ i: 0 });
        await When["the user presses the space key"]();
        await Then["the row {i} should be selected"]({ i: 0 });
      }
    );
  });
  test.describe("Integration with MUI DataGrid Features", () => {
    feature(
      "Sorting works correctly with radio selection",
      async ({ Given, When, Then }) => {
        await Given["the data grid with additional features is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await Then["the row {i} should be selected"]({ i: 0 });
        await When["the user clicks the sort button for column {field}"]({
          field: "age",
        });
        // Selection should now move to the third row, as John is the oldest
        await Then["the row {i} should be selected"]({ i: 2 });
      }
    );
    feature(
      "Pagination works correctly with radio selection",
      async ({ Given, When, Then }) => {
        await Given["the data grid with additional features is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await Then["the row {i} should be selected"]({ i: 0 });

        await When["the user navigates to the next page"]();
        // Selection should be maintained in the data model, even if row is not visible

        await Then["the selection indicator should show {id}"]({ id: 1 });
        // When returning to first page, the selection should still be there
        await When["the user navigates to the previous page"]();
        await Then["the row {i} should be selected"]({ i: 0 });
      }
    );
    feature(
      "Changing page size works correctly with radio selection",
      async ({ Given, When, Then }) => {
        await Given["the data grid with additional features is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 2,
        });
        await Then["the row {i} should be selected"]({ i: 2 });
        await When["the user changes the page size to {size}"]({ size: 5 });
        await Then["the row {i} should be selected"]({ i: 2 });
      }
    );
  });
});
