import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import materialTheme from "../../../../theme";
import StatusFilter from "../StatusFilter";
import { ThemeProvider } from "@mui/material/styles";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("StatusFilter", () => {
  test("Current status should have aria-current property", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <StatusFilter
          current="EXCLUDE"
          onClose={() => {}}
          anchorEl={document.createElement("div")}
        />
      </ThemeProvider>
    );

    expect(
      screen.getByRole("menuitem", {
        name: "Current",
      })
    ).toHaveAttribute("aria-current", "true");
  });
});


