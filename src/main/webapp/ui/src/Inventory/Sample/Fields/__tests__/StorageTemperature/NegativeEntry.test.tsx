import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { CELSIUS } from "../../../../../stores/definitions/Units";
import materialTheme from "../../../../../theme";
import SpecifiedStorageTemperature from "../../SpecifiedStorageTemperature";

const MIN_LABEL = "inventory:sample.fields.storageTemperature.min";

const setTemperatures = vi.fn();

const renderField = (minValue: number) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <SpecifiedStorageTemperature
        setTemperatures={setTemperatures}
        setFieldEditable={() => {}}
        storageTempMin={{ numericValue: minValue, unitId: CELSIUS }}
        storageTempMax={{ numericValue: 30, unitId: CELSIUS }}
        disabled={false}
        canChooseWhichToEdit={false}
        onErrorStateChange={() => {}}
      />
    </ThemeProvider>,
  );

describe("SpecifiedStorageTemperature, when entering a temperature below zero, should", () => {
  beforeEach(() => {
    setTemperatures.mockClear();
  });

  test("keep the minus sign once every digit has been deleted.", async () => {
    const user = userEvent.setup();
    renderField(-20);
    const min = screen.getByLabelText(MIN_LABEL);

    await user.click(min);
    await user.keyboard("{End}{Backspace}{Backspace}");

    expect(min).toHaveValue("-");
  });

  test("accept a value typed minus sign first.", async () => {
    const user = userEvent.setup();
    renderField(20);
    const min = screen.getByLabelText(MIN_LABEL);

    await user.clear(min);
    await user.type(min, "-5");

    expect(min).toHaveValue("-5");
    expect(setTemperatures).toHaveBeenLastCalledWith({
      storageTempMin: { numericValue: -5, unitId: CELSIUS },
      storageTempMax: { numericValue: 30, unitId: CELSIUS },
    });
  });
});
