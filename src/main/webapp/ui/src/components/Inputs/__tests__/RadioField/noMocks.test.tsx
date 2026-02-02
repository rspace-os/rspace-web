import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
  fireEvent,
} from "@testing-library/react";
import RadioField from "../../RadioField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
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
      </ThemeProvider>
    );
    fireEvent.click(screen.getByRole("radio", { name: "FirstOption" }));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          name: "fieldName",
          value: "firstOption",
        }) as HTMLInputElement,
      })
    );
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});

