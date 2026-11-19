import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CELSIUS, KELVIN, type Temperature } from "../../../../../stores/definitions/Units";
import materialTheme from "../../../../../theme";
import SpecifiedStorageTemperature from "../../SpecifiedStorageTemperature";

const temperature = (numericValue: number): Temperature => ({ numericValue, unitId: CELSIUS });

describe("SpecifiedStorageTemperature", () => {
  it("marks the section in error only while the range is invalid", async () => {
    const user = userEvent.setup();
    const onErrorStateChange = vi.fn();
    const setTemperatures = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <SpecifiedStorageTemperature
          setTemperatures={setTemperatures}
          setFieldEditable={() => {}}
          storageTempMin={temperature(15)}
          storageTempMax={temperature(30)}
          disabled={false}
          canChooseWhichToEdit={false}
          onErrorStateChange={onErrorStateChange}
        />
      </ThemeProvider>,
    );
    const [min] = screen.getAllByRole("spinbutton");

    await user.clear(min);
    await user.type(min, "4");
    expect(onErrorStateChange).toHaveBeenLastCalledWith(false);

    await user.clear(min);
    await user.type(min, "-300");
    expect(onErrorStateChange).toHaveBeenLastCalledWith(true);

    // still below absolute zero in kelvin, so changing the unit reports the range in error again
    onErrorStateChange.mockClear();
    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: "K" }));
    expect(setTemperatures).toHaveBeenLastCalledWith(
      expect.objectContaining({ storageTempMin: expect.objectContaining({ unitId: KELVIN }) }),
    );
    expect(onErrorStateChange).toHaveBeenCalledExactlyOnceWith(true);

    await user.click(
      screen.getByRole("button", { name: "inventory:sample.fields.storageTemperature.presets.refrigerated" }),
    );
    expect(onErrorStateChange).toHaveBeenLastCalledWith(false);
  });
});
