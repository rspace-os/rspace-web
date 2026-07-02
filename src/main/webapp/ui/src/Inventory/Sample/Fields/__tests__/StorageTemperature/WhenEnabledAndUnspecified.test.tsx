import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type { HasEditableFields } from "../../../../../stores/definitions/Editable";
import { CELSIUS, type Temperature } from "../../../../../stores/definitions/Units";
import materialTheme from "../../../../../theme";
import StorageTemperature from "../../StorageTemperature";

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
const mockFieldOwner = (mockedParts: MockFieldOwnerProps): HasEditableFields<MockFields> => ({
  isFieldEditable: vi.fn().mockImplementation((_field: string) => mockedParts.isFieldEditable()),
  fieldValues: mockedParts.fieldValues,
  setFieldsDirty: vi.fn(),
  canChooseWhichToEdit: false,
  setFieldEditable: vi.fn(),
  noValueLabel: mockedParts.noValueLabel,
});
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

      const spy = vi.spyOn(fieldOwner, "setFieldsDirty");
      render(
        <ThemeProvider theme={materialTheme}>
          <StorageTemperature fieldOwner={fieldOwner} onErrorStateChange={() => {}} />
        </ThemeProvider>,
      );
      await user.click(screen.getByText("inventory:sample.fields.storageTemperature.specify"));
      expect(spy).toHaveBeenCalledWith({
        storageTempMin: { numericValue: 15, unitId: CELSIUS },
        storageTempMax: { numericValue: 30, unitId: CELSIUS },
      });
    });
  });
});
