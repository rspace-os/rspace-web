/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import materialTheme from "../../../../theme";
import StatusFilter from "../StatusFilter";
import { ThemeProvider } from "@mui/material/styles";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
