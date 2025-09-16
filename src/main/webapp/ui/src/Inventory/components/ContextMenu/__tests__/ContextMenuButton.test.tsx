/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import fc from "fast-check";
import ContextMenuButton from "../ContextMenuButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
