import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import Quantity from "../Quantity";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      unitsOfCategory: vi.fn(() => [
        { id: 5, label: "µg", category: "mass" },
        { id: 6, label: "mg", category: "mass" },
        { id: 7, label: "g", category: "mass" },
      ]),
      getUnit: vi.fn(),
    },
  }),
}));
describe("Quantity", () => {
  test("Should support scientific notation.", () => {
    const INITIAL_VALUE = 0;

    const setFieldsDirty = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <Quantity
          onErrorStateChange={() => {}}
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              quantity: {
                numericValue: INITIAL_VALUE,
                unitId: 5,
              },
            },
            setFieldsDirty,
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: {
              quantity: null,
            },
          }}
          quantityCategory="mass"
        />
      </ThemeProvider>,
    );
    const input = screen.getByDisplayValue(INITIAL_VALUE);

    fireEvent.input(input, { target: { value: "4e-2" } });
    expect(setFieldsDirty).toHaveBeenCalledWith({
      quantity: {
        numericValue: 0.04,
        unitId: 5,
      },
    });
  });
});
