import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import TypeFilter from "../TypeFilter";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";




describe("TypeFilter", () => {
  test("Current type should have aria-current property", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <TypeFilter
          current="ALL"
          onClose={() => {}}
          anchorEl={document.createElement("div")}
        />
      </ThemeProvider>
    );

    expect(
      screen.getByRole("menuitem", {
        name: /All/,
      })
    ).toHaveAttribute("aria-current", "true");
  });
});


