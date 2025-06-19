/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import StorageTemperature from "../../StorageTemperature";
import {
  CELSIUS,
  type Temperature,
} from "../../../../../stores/definitions/Units";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import userEvent from "@testing-library/user-event";
import { type HasEditableFields } from "../../../../../stores/definitions/Editable";

type MockFields = {
  storageTempMin: Temperature | null;
  storageTempMax: Temperature | null;
};

type MockFieldOwnerProps = {
  fieldValues: {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  };
  isFieldEditable: () => boolean;
  noValueLabel: {
    storageTempMin: string | null;
    storageTempMax: string | null;
  };
};

const mockFieldOwner = (
  mockedParts: MockFieldOwnerProps
): HasEditableFields<MockFields> => ({
  isFieldEditable: jest
    .fn()
    .mockImplementation((_field: string) => mockedParts.isFieldEditable()),
  fieldValues: mockedParts.fieldValues,
  setFieldsDirty: jest.fn(),
  canChooseWhichToEdit: false,
  setFieldEditable: jest.fn(),
  noValueLabel: mockedParts.noValueLabel,
});

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("StorageTemperature", () => {
  describe("When enabled and unspecified, the component should", () => {
    test("show a button that when tapped defaults to ambient temperature.", async () => {
      const user = userEvent.setup();
      const fieldOwner = mockFieldOwner({
        fieldValues: {
          storageTempMin: null,
          storageTempMax: null,
        },
        noValueLabel: {
          storageTempMin: "unspecified",
          storageTempMax: "unspecified",
        },
        isFieldEditable: () => true,
      });
      const spy = jest.spyOn(fieldOwner, "setFieldsDirty");

      render(
        <ThemeProvider theme={materialTheme}>
          <StorageTemperature
            fieldOwner={fieldOwner}
            onErrorStateChange={() => {}}
          />
        </ThemeProvider>
      );
      await user.click(screen.getByText("Specify"));
      expect(spy).toHaveBeenCalledWith({
        storageTempMin: { numericValue: 15, unitId: CELSIUS },
        storageTempMax: { numericValue: 30, unitId: CELSIUS },
      });
    });
  });
});
