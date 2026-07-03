import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import { describe, expect, test, vi } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import { CELSIUS, FAHRENHEIT, KELVIN, type Temperature } from "../../../../../stores/definitions/Units";

import materialTheme from "../../../../../theme";

import StorageTemperature from "../../StorageTemperature";

const mockFieldOwner = (mockedParts: {
  fieldValues: {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  };
  isFieldEditable: () => boolean;
  noValueLabel: {
    storageTempMin: string | null;
    storageTempMax: string | null;
  };
}) => {
  const defaults = {
    isFieldEditable: vi.fn().mockImplementation(() => false),
    fieldValues: {
      storageTempMin: null,
      storageTempMax: null,
    },
    setFieldsDirty: vi.fn(),
    canChooseWhichToEdit: false,
    setFieldEditable: vi.fn(),
    noValueLabel: {
      storageTempMin: null,
      storageTempMax: null,
    },
  };
  return {
    ...defaults,
    ...mockedParts,
  };
};
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
          </ThemeProvider>,
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
          </ThemeProvider>,
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
          </ThemeProvider>,
        );
        expect(container).toHaveTextContent("unspecified");
      });
    });
    describe("Display a string when both values are valid temperatures,", () => {
      test("That includer both min and max values.", async () => {
        const i18n = await createTestI18n({ inventory: inventoryEn }, "inventory");
        const { container } = render(
          <I18nextProvider i18n={i18n}>
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
          </I18nextProvider>,
        );
        expect(container).toHaveTextContent("Between 1°C and 2°C.");
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
            </ThemeProvider>,
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
            </ThemeProvider>,
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
            </ThemeProvider>,
          );
          expect(container).toHaveTextContent("0°F");
        });
      });
    });
  });
});
