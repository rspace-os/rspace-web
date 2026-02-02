import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  CELSIUS,
  type Temperature,
} from "../../../../../stores/definitions/Units";
import StorageTemperature from "../../StorageTemperature";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

vi.mock("@mui/material/Button", () => ({
  default: vi.fn(() => <></>),
}));

const mockFieldOwner = (mockedParts: {
  fieldValues: {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  };
  isFieldEditable: () => boolean;
}) => {
  const defaults = {
    isFieldEditable: vi.fn(() => false),
    fieldValues: {},
    setFieldsDirty: vi.fn(),
    canChooseWhichToEdit: false,
    setFieldEditable: vi.fn(),
    noValueLabel: {
      storageTempMin: null,
      storageTempMax: null,
    },
  };

  return { ...defaults, ...mockedParts };
};

beforeEach(() => {
  vi.clearAllMocks();
});


describe("StorageTemperature", () => {
  describe("Buttons", () => {
    it("Five helper buttons should be shown when the temperature is editable.", () => {
      const fieldOwner = mockFieldOwner({
        fieldValues: {
          storageTempMin: { numericValue: 0, unitId: CELSIUS },
          storageTempMax: { numericValue: 0, unitId: CELSIUS },
        },
        isFieldEditable: () => true,
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <StorageTemperature
            fieldOwner={fieldOwner}
            onErrorStateChange={() => {}}
          />
        </ThemeProvider>
      );

      expect(Button).toHaveBeenCalledTimes(5);
    });
  });
});
