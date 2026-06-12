import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { render, within } from "@/__tests__/customQueries";
import {
  ControlledDataGridWithRadioSelectionExample,
  DataGridWithRadioSelectionExample,
} from "./DataGridWithRadioSelection.story";

/*
 * jsdom conversion of DataGridWithRadioSelection.spec.tsx.
 *
 * Under vitest, `@mui/x-data-grid` resolves to src/test-stubs/MuiDataGridStub.tsx.
 * The stub renders rows/cells and invokes `renderCell`/`renderHeader`, so the
 * component's own radio rendering and selection logic run for real. Notes:
 *
 *   - The story supplies `getRowId={(row) => row.id}` (the real-MUI default),
 *     so the stub emits the stable `id` field (1, 2, 3, ...) to `renderCell` /
 *     `onSelectionChange`, matching what production emits. Assertions below use
 *     those ids: John = 1, Jane = 2, Bob = 3.
 *   - The stub does NOT set `aria-selected` on rows, so the row-level
 *     `aria-selected="true"` assertion from the spec is dropped here; selection
 *     is verified via the component-driven radio `checked` state instead.
 *   - Because `checkboxSelection` is set, the stub also renders its own native
 *     "Select row" checkbox column. Radios are selected by their accessible
 *     label ("Select John Doe", ...) so tests stay independent of that extra
 *     column and of row ordering.
 *
 * Cases that depend on real MUI sorting / pagination / page-size / column
 * layout stay in DataGridWithRadioSelection.spec.tsx (the stub cannot reproduce
 * them).
 */

const dataRows = () => screen.getAllByRole("row").slice(1);

const radioFor = (name: string): HTMLInputElement => screen.getByRole<HTMLInputElement>("radio", { name });

describe("DataGridWithRadioSelection", () => {
  describe("Basic Rendering and Initial State", () => {
    test("No row is selected by default", () => {
      render(<DataGridWithRadioSelectionExample />);
      expect(screen.queryAllByRole("radio").filter((radio) => (radio as HTMLInputElement).checked)).toHaveLength(0);
      expect(screen.getByTestId("selection-indicator")).toHaveTextContent("Nothing selected");
    });

    test("Radio buttons have the correct ARIA labels", () => {
      render(<DataGridWithRadioSelectionExample />);
      const firstRow = dataRows()[0];
      expect(within(firstRow).getByRole("radio")).toHaveAttribute("aria-label", "Select John Doe");
    });
  });

  describe("Radio Selection Behavior", () => {
    test("Clicking a radio button selects the corresponding row", async () => {
      const user = userEvent.setup();
      render(<DataGridWithRadioSelectionExample />);

      await user.click(radioFor("Select John Doe"));

      expect(radioFor("Select John Doe").checked).toBe(true);
      // John's stable row id is 1.
      expect(screen.getByTestId("selection-indicator")).toHaveTextContent("Selected ID: 1");
    });

    test("Selecting a new row deselects the previously selected row", async () => {
      const user = userEvent.setup();
      render(<DataGridWithRadioSelectionExample />);

      await user.click(radioFor("Select John Doe"));
      expect(radioFor("Select John Doe").checked).toBe(true);

      await user.click(radioFor("Select Jane Smith"));
      expect(radioFor("Select Jane Smith").checked).toBe(true);
      expect(radioFor("Select John Doe").checked).toBe(false);
      expect(screen.getByTestId("selection-indicator")).toHaveTextContent("Selected ID: 2");
    });

    test("Clicking on a row cell maintains the selection state", async () => {
      const user = userEvent.setup();
      render(<DataGridWithRadioSelectionExample />);

      await user.click(radioFor("Select John Doe"));

      // Click a non-radio cell of another row; this must not change selection.
      const janeRow = dataRows()[1];
      const janeCells = within(janeRow).getAllByRole("gridcell");
      await user.click(janeCells[2]);

      expect(radioFor("Select John Doe").checked).toBe(true);
      expect(screen.getByTestId("selection-indicator")).toHaveTextContent("Selected ID: 1");
    });
  });

  describe("Controlled vs Uncontrolled Mode", () => {
    test("Uncontrolled mode manages internal selection state", async () => {
      const user = userEvent.setup();
      render(<DataGridWithRadioSelectionExample />);

      await user.click(radioFor("Select John Doe"));
      expect(radioFor("Select John Doe").checked).toBe(true);

      await user.click(radioFor("Select Jane Smith"));
      expect(radioFor("Select Jane Smith").checked).toBe(true);
    });

    test("Controlled mode reflects external selection state", async () => {
      const user = userEvent.setup();
      // selectedRowId is the stable row id, so an initial value of 1 selects
      // John (id 1).
      render(<ControlledDataGridWithRadioSelectionExample initialSelectedRowId={1} />);

      expect(radioFor("Select John Doe").checked).toBe(true);

      // External state change via the Reset button.
      await user.click(screen.getByRole("button", { name: /Reset Selection/i }));
      expect(screen.queryAllByRole("radio").filter((radio) => (radio as HTMLInputElement).checked)).toHaveLength(0);

      // The "Select Row 2" button sets selectedRowId to 2, which is Jane's id.
      await user.click(screen.getByRole("button", { name: /Select Row 2/i }));
      expect(radioFor("Select Jane Smith").checked).toBe(true);
    });

    test("onSelectionChange callback is called with correct row ID", async () => {
      const user = userEvent.setup();
      const spy = vi.fn();
      render(<ControlledDataGridWithRadioSelectionExample initialSelectedRowId={null} onSelectionChangeSpy={spy} />);

      await user.click(radioFor("Select Bob Johnson"));

      // Bob's stable row id is 3.
      expect(spy).toHaveBeenCalledWith(3);
      expect(radioFor("Select Bob Johnson").checked).toBe(true);
    });
  });

  describe("Keyboard selection (space on a focused radio)", () => {
    // page.keyboard.press("Tab") traversal is NOT reproducible in the stub
    // (no real grid Tab order), so the two real-Tab keyboard tests stay in
    // Playwright. Selecting via Space on an already-focused radio is the
    // component's own onKeyDown handler and converts.
    test("Pressing space on a focused radio selects the row", async () => {
      const user = userEvent.setup();
      render(<DataGridWithRadioSelectionExample />);

      const radio = radioFor("Select John Doe");
      radio.focus();
      await user.keyboard(" ");

      expect(radio.checked).toBe(true);
      expect(screen.getByTestId("selection-indicator")).toHaveTextContent("Selected ID: 1");
    });
  });
});
