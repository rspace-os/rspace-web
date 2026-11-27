import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import Quantity from "../Quantity";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({}),
}));
describe("Quantity", () => {
  test("Should support scientific notation.", async () => {
    const user = userEvent.setup();
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

    await user.clear(input);
    await user.paste("4e-2");
    expect(setFieldsDirty).toHaveBeenCalledWith({
      quantity: {
        numericValue: 0.04,
        unitId: 5,
      },
    });
  });
});
