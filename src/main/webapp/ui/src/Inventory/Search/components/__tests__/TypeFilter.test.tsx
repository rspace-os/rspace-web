import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test } from "vitest";
import materialTheme from "../../../../theme";
import TypeFilter from "../TypeFilter";

describe("TypeFilter", () => {
  test("Current type should have aria-current property", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <TypeFilter current="ALL" onClose={() => {}} anchorEl={document.createElement("div")} />
      </ThemeProvider>,
    );
    expect(
      screen.getByRole("menuitem", {
        name: /All/,
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
