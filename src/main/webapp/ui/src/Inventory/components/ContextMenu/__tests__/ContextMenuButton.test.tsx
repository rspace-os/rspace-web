import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  cleanup,
  screen,
} from "@testing-library/react";
import fc from "fast-check";
import ContextMenuButton from "../ContextMenuButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";




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


