/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, fireEvent, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import Quantity from "../Quantity";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { type Quantity as QuantityType } from "../../../../stores/definitions/HasQuantity";

jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    unitsOfCategory: jest.fn(() => [
      { id: 5, label: "µg", category: "mass" },
      { id: 6, label: "mg", category: "mass" },
      { id: 7, label: "g", category: "mass" },
    ]),
    getUnit: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Quantity", () => {
  test("Should support scientific notation.", () => {
    const INITIAL_VALUE = 0;

    const setFieldsDirty = jest.fn();

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
      </ThemeProvider>
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
