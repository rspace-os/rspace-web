/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import StorageTemperature from "../../StorageTemperature";
import { CELSIUS } from "../../../../../stores/definitions/Units";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import { type Temperature } from "../../../../../stores/definitions/Sample";

const mockFieldOwner = (mockedParts: {|
  fieldValues: {
    storageTempMin: ?Temperature,
    storageTempMax: ?Temperature,
    ...
  },
  isFieldEditable: () => boolean,
  noValueLabel: {
    storageTempMin: ?string,
    storageTempMax: ?string,
    ...
  },
|}) => ({
  isFieldEditable: jest.fn<[string], boolean>(),
  fieldValues: {},
  setFieldsDirty: jest.fn<
    [{ storageTempMin: ?Temperature, storageTempMax: ?Temperature }],
    void
  >(),
  canChooseWhichToEdit: false,
  setFieldEditable: jest.fn<[string, boolean], void>(),
  noValueLabel: {},
  ...mockedParts,
});

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("StorageTemperature", () => {
  describe("When enabled and unspecified, the component should", () => {
    test("show a button that when tapped defaults to ambient temperature.", () => {
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
      screen.getByText("Specify").click();
      expect(spy).toHaveBeenCalledWith({
        storageTempMin: { numericValue: 15, unitId: CELSIUS },
        storageTempMax: { numericValue: 30, unitId: CELSIUS },
      });
    });
  });
});
