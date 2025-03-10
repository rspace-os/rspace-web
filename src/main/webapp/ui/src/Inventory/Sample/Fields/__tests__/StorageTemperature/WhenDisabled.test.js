/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import {
  CELSIUS,
  KELVIN,
  FAHRENHEIT,
  type Temperature,
} from "../../../../../stores/definitions/Units";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

import StorageTemperature from "../../StorageTemperature";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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

describe("StorageTemperature", () => {
  describe("When disabled, the component should,", () => {
    describe('Show "Unspecified" when,', () => {
      test("`storageTempMin` is null.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StorageTemperature
              onErrorStateChange={() => {}}
              fieldOwner={mockFieldOwner({
                fieldValues: {
                  storageTempMin: null,
                  storageTempMax: { numericValue: 0, unitId: CELSIUS },
                },
                noValueLabel: {
                  storageTempMin: "unspecified",
                  storageTempMax: "unspecified",
                },
                isFieldEditable: () => false,
              })}
            />
          </ThemeProvider>
        );

        expect(container).toHaveTextContent("unspecified");
      });
      test("`storageTempMax` is null.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StorageTemperature
              onErrorStateChange={() => {}}
              fieldOwner={mockFieldOwner({
                fieldValues: {
                  storageTempMin: { numericValue: 0, unitId: CELSIUS },
                  storageTempMax: null,
                },
                noValueLabel: {
                  storageTempMin: "unspecified",
                  storageTempMax: "unspecified",
                },
                isFieldEditable: () => false,
              })}
            />
          </ThemeProvider>
        );

        expect(container).toHaveTextContent("unspecified");
      });
      test("Both values are null.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StorageTemperature
              onErrorStateChange={() => {}}
              fieldOwner={mockFieldOwner({
                fieldValues: {
                  storageTempMin: null,
                  storageTempMax: null,
                },
                noValueLabel: {
                  storageTempMin: "unspecified",
                  storageTempMax: "unspecified",
                },
                isFieldEditable: () => false,
              })}
            />
          </ThemeProvider>
        );

        expect(container).toHaveTextContent("unspecified");
      });
    });

    describe("Display a string when both values are valid temperatures,", () => {
      test("That includer both min and max values.", () => {
        const { container } = render(
          <ThemeProvider theme={materialTheme}>
            <StorageTemperature
              onErrorStateChange={() => {}}
              fieldOwner={mockFieldOwner({
                fieldValues: {
                  storageTempMin: { numericValue: 1, unitId: CELSIUS },
                  storageTempMax: { numericValue: 2, unitId: CELSIUS },
                },
                noValueLabel: {
                  storageTempMin: "unspecified",
                  storageTempMax: "unspecified",
                },
                isFieldEditable: () => false,
              })}
            />
          </ThemeProvider>
        );

        expect(container).toHaveTextContent("1");
        expect(container).toHaveTextContent("2");
      });
      describe("That renders the units correctly.", () => {
        test("Celsius", () => {
          const { container } = render(
            <ThemeProvider theme={materialTheme}>
              <StorageTemperature
                onErrorStateChange={() => {}}
                fieldOwner={mockFieldOwner({
                  fieldValues: {
                    storageTempMin: { numericValue: 0, unitId: CELSIUS },
                    storageTempMax: { numericValue: 0, unitId: CELSIUS },
                  },
                  noValueLabel: {
                    storageTempMin: "unspecified",
                    storageTempMax: "unspecified",
                  },
                  isFieldEditable: () => false,
                })}
              />
            </ThemeProvider>
          );

          expect(container).toHaveTextContent("0°C");
        });
        test("Kelvin", () => {
          const { container } = render(
            <ThemeProvider theme={materialTheme}>
              <StorageTemperature
                onErrorStateChange={() => {}}
                fieldOwner={mockFieldOwner({
                  fieldValues: {
                    storageTempMin: { numericValue: 0, unitId: KELVIN },
                    storageTempMax: { numericValue: 0, unitId: KELVIN },
                  },
                  noValueLabel: {
                    storageTempMin: "unspecified",
                    storageTempMax: "unspecified",
                  },
                  isFieldEditable: () => false,
                })}
              />
            </ThemeProvider>
          );

          expect(container).toHaveTextContent("0K");
        });
        test("Fahrenheit", () => {
          const { container } = render(
            <ThemeProvider theme={materialTheme}>
              <StorageTemperature
                onErrorStateChange={() => {}}
                fieldOwner={mockFieldOwner({
                  fieldValues: {
                    storageTempMin: { numericValue: 0, unitId: FAHRENHEIT },
                    storageTempMax: { numericValue: 0, unitId: FAHRENHEIT },
                  },
                  noValueLabel: {
                    storageTempMin: "unspecified",
                    storageTempMax: "unspecified",
                  },
                  isFieldEditable: () => false,
                })}
              />
            </ThemeProvider>
          );

          expect(container).toHaveTextContent("0°F");
        });
      });
    });
  });
});
