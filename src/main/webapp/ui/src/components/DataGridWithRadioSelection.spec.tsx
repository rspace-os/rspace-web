import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  DataGridWithRadioSelectionExample,
  ControlledDataGridWithRadioSelectionExample,
  DataGridWithFeatures,
} from "./DataGridWithRadioSelection.story";
import { GridRowId } from "@mui/x-data-grid";

const createSelectionChangeSpy = () => {
  let lastSelectedId: GridRowId | null = null;

  const handler = (id: GridRowId) => {
    lastSelectedId = id;
  };

  const getLastSelectedId = () => lastSelectedId;

  return {
    handler,
    getLastSelectedId,
  };
};

const feature = test.extend<{
  Given: {
    "the data grid with radio selection is rendered": () => Promise<void>;
    "the data grid with controlled selection is rendered": (params?: {
      selectedRowId?: GridRowId | null;
    }) => Promise<{ spy: ReturnType<typeof createSelectionChangeSpy> }>;
    "the data grid with additional features is rendered": () => Promise<void>;
  };
  When: {
    "the user clicks on the radio button for row {i}": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the user clicks on row {i}": ({ i }: { i: number }) => Promise<void>;
    "the user tabs to focus the grid": () => Promise<void>;
    "the user tabs to the radio button for row {i}": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the user presses the space key": () => Promise<void>;
    "the user clicks the reset selection button": () => Promise<void>;
    "the user clicks the 'Select Row 2' button": () => Promise<void>;
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
    "the row {i} should not be selected": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the selection indicator should show {id}": ({
      id,
    }: {
      id: number | null;
    }) => Promise<void>;
    "no rows should be selected": () => Promise<void>;
    "the radio button for row {i} should have focus": ({
      i,
    }: {
      i: number;
    }) => Promise<void>;
    "the radio button for row {i} should have the aria-label {ariaLabel}": ({
      i,
    }: {
      i: number;
      ariaLabel: string;
    }) => Promise<void>;
    "the {spy} should have been called with {id}": ({
      id,
      spy,
    }: {
      id: number;
      spy: ReturnType<typeof createSelectionChangeSpy>;
    }) => void;
    "the radio column should be the first column": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the data grid with radio selection is rendered": async () => {
        await mount(<DataGridWithRadioSelectionExample />);
      },
      "the data grid with controlled selection is rendered": async ({
        selectedRowId = null,
      } = {}) => {
        const selectionChangeSpy = createSelectionChangeSpy();
        await mount(
          <ControlledDataGridWithRadioSelectionExample
            initialSelectedRowId={selectedRowId}
            onSelectionChangeSpy={selectionChangeSpy.handler}
          />
        );
        return { spy: selectionChangeSpy };
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
      "the user clicks on row {i}": async ({ i }) => {
        await page
          .getByRole("row")
          .nth(i + 1)
          .getByRole("gridcell")
          // Click on a cell in the row (not the radio button)
          .nth(2)
          .click();
      },
      "the user tabs to focus the grid": async () => {
        await page.keyboard.press("Tab");
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
      "the user clicks the reset selection button": async () => {
        await page.getByRole("button", { name: /Reset Selection/i }).click();
      },
      "the user clicks the 'Select Row 2' button": async () => {
        await page.getByRole("button", { name: /Select Row 2/i }).click();
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
      "the row {i} should not be selected": async ({ i }) => {
        await expect(
          page
            .getByRole("row")
            .nth(i + 1)
            .getByRole("radio")
        ).not.toBeChecked();
        await expect(page.getByRole("row").nth(i + 1)).not.toHaveAttribute(
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
      "no rows should be selected": async () => {
        await expect(page.getByRole("radio", { checked: true })).toHaveCount(0);
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
      "the radio button for row {i} should have the aria-label {ariaLabel}":
        async ({ i, ariaLabel }) => {
          const row = page.getByRole("row").nth(i + 1);
          await expect(row.getByRole("radio")).toHaveAttribute(
            "aria-label",
            ariaLabel
          );
        },
      "the {spy} should have been called with {id}": ({ id, spy }) => {
        expect(spy.getLastSelectedId()).toBe(id);
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

    feature("No row is selected by default", async ({ Given, Then }) => {
      await Given["the data grid with radio selection is rendered"]();
      await Then["no rows should be selected"]();
      await Then["the selection indicator should show {id}"]({ id: null });
    });

    feature(
      "Radio buttons have the correct ARIA labels",
      async ({ Given, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await Then[
          "the radio button for row {i} should have the aria-label {ariaLabel}"
        ]({ i: 0, ariaLabel: "Select John Doe" });
      }
    );
  });

  test.describe("Radio Selection Behavior", () => {
    feature(
      "Clicking a radio button selects the corresponding row",
      async ({ Given, When, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await Then["the row {i} should be selected"]({ i: 0 });
        await Then["the selection indicator should show {id}"]({ id: 1 });
      }
    );

    feature(
      "Selecting a new row deselects the previously selected row",
      async ({ Given, When, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await Then["the row {i} should be selected"]({ i: 0 });

        await When["the user clicks on the radio button for row {i}"]({
          i: 1,
        });
        await Then["the row {i} should be selected"]({ i: 1 });
        await Then["the row {i} should not be selected"]({ i: 0 });
        await Then["the selection indicator should show {id}"]({ id: 2 });
      }
    );

    feature(
      "Clicking on a row cell maintains the selection state",
      async ({ Given, When, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await When["the user clicks on row {i}"]({ i: 1 });

        // Selection should remain on row 1
        await Then["the row {i} should be selected"]({ i: 0 });
        await Then["the selection indicator should show {id}"]({ id: 1 });
      }
    );
  });

  test.describe("Controlled vs Uncontrolled Mode", () => {
    feature(
      "Uncontrolled mode manages internal selection state",
      async ({ Given, When, Then }) => {
        await Given["the data grid with radio selection is rendered"]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 0,
        });
        await Then["the row {i} should be selected"]({ i: 0 });

        await When["the user clicks on the radio button for row {i}"]({
          i: 1,
        });
        await Then["the row {i} should be selected"]({ i: 1 });
      }
    );

    feature(
      "Controlled mode reflects external selection state",
      async ({ Given, When, Then }) => {
        await Given["the data grid with controlled selection is rendered"]({
          selectedRowId: 1,
        });
        await Then["the row {i} should be selected"]({ i: 0 });

        // External state change
        await When["the user clicks the reset selection button"]();
        await Then["no rows should be selected"]();

        await When["the user clicks the 'Select Row 2' button"]();
        await Then["the row {i} should be selected"]({ i: 1 });
      }
    );

    feature(
      "onSelectionChange callback is called with correct row ID",
      async ({ Given, When, Then }) => {
        const { spy } = await Given[
          "the data grid with controlled selection is rendered"
        ]();
        await When["the user clicks on the radio button for row {i}"]({
          i: 2,
        });
        Then["the {spy} should have been called with {id}"]({
          id: 3,
          spy,
        });
        await Then["the row {i} should be selected"]({ i: 2 });
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
