import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CELSIUS, type Temperature } from "../../../../../stores/definitions/Units";
import materialTheme from "../../../../../theme";
import SpecifiedStorageTemperature from "../../SpecifiedStorageTemperature";

const temperature = (numericValue: number): Temperature => ({ numericValue, unitId: CELSIUS });

describe("SpecifiedStorageTemperature", () => {
  it("marks the section in error only while the range is invalid", async () => {
    const user = userEvent.setup();
    const onErrorStateChange = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <SpecifiedStorageTemperature
          setTemperatures={() => {}}
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

    await user.click(
      screen.getByRole("button", { name: "inventory:sample.fields.storageTemperature.presets.refrigerated" }),
    );
    expect(onErrorStateChange).toHaveBeenLastCalledWith(false);
  });
});
