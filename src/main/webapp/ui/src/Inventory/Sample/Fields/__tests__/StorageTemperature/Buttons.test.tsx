/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
/// <reference types="jest" />
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import {
  CELSIUS,
  type Temperature,
} from "../../../../../stores/definitions/Units";
import StorageTemperature from "../../StorageTemperature";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

jest.mock("@mui/material/Button", () => jest.fn(() => <></>));

const mockFieldOwner = (mockedParts: {
  fieldValues: {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  };
  isFieldEditable: () => boolean;
}) => {
  const defaults = {
    isFieldEditable: jest.fn(() => false),
    fieldValues: {},
    setFieldsDirty: jest.fn(),
    canChooseWhichToEdit: false,
    setFieldEditable: jest.fn(),
    noValueLabel: {
      storageTempMin: null,
      storageTempMax: null,
    },
  };

  return { ...defaults, ...mockedParts };
};

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("StorageTemperature", () => {
  describe("Buttons", () => {
    test("Five helper buttons should be shown when the temperature is editable.", () => {
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
