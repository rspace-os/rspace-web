import { test, describe, expect, vi } from 'vitest';
import React from "react";

import { render, screen } from "@testing-library/react";
import FileField from "../FileField";
import Alert from "@mui/material/Alert";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

vi.mock("@mui/material/Alert", () => ({
  default: vi.fn(() => {
    return <div data-testid="Alert"></div>;

  }),
}));
describe("FileField", () => {
  /*
   * The warningAlert allows for custom warning meesages to be associated with
   * a file field, useful in reporting issues around invalid data or providing
   * general help.
   */
  describe("When passed a warningAlert there should", () => {
    test("be an Alert on the page.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FileField
            onChange={() => {}}
            name="foo"
            accept="foo"
            warningAlert="A warning alert."
          />
        </ThemeProvider>
      );
      expect(screen.getByTestId("Alert")).toBeInTheDocument();
      expect(Alert).toHaveBeenCalledWith(
        expect.objectContaining({
          children: "A warning alert.",
          severity: "warning",
        }),
        expect.anything()
      );
    });
  });

  test("renders input slot adornments passed through slotProps", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <FileField
          onChange={() => {}}
          accept="foo"
          slotProps={{
            input: {
              startAdornment: <div>before</div>,
              endAdornment: <div>after</div>,
            },
          }}
        />
      </ThemeProvider>,
    );

    expect(screen.getByText("before")).toBeInTheDocument();
    expect(screen.getByText("after")).toBeInTheDocument();
  });
});

