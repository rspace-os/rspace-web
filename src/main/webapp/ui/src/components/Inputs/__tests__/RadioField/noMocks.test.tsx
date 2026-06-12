import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import RadioField from "../../RadioField";

describe("RadioField", () => {
  test("When a selection is made, the onChange handler is called", () => {
    const onChange = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <RadioField
          onChange={onChange}
          name="fieldName"
          options={[
            {
              label: "FirstOption",
              value: "firstOption",
              editing: false,
            },
            {
              label: "SecondOption",
              value: "secondOption",
              editing: false,
            },
          ]}
          value={null}
        />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("radio", { name: "FirstOption" }));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          name: "fieldName",
          value: "firstOption",
        }) as HTMLInputElement,
      }),
    );
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
