import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen } from "@testing-library/react";
import fc from "fast-check";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test } from "vitest";
import materialTheme from "../../../../theme";
import ContextMenuButton from "../ContextMenuButton";

describe("ContextMenuButton", () => {
  describe("Disabled state", () => {
    test("When disabled, should render aria-disabled.", () => {
      fc.assert(
        fc.property(fc.string(), (disabledHelp) => {
          cleanup();
          render(
            <ThemeProvider theme={materialTheme}>
              <ContextMenuButton disabledHelp={disabledHelp} label="Foo" />
            </ThemeProvider>,
          );
          expect(screen.getByRole("button", { name: "Foo" })).toHaveAttribute(
            "aria-disabled",
            (disabledHelp !== "").toString(),
          );
        }),
      );
    });
  });
});
