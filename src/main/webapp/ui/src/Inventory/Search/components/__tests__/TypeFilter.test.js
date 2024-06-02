/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import TypeFilter from "../TypeFilter";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
