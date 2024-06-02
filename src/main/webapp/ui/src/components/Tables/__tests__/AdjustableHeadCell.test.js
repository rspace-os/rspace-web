/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import AdjustableHeadCell from "../AdjustableHeadCell";
import RsSet from "../../../util/set";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("AdjustableHeadCell", () => {
  describe("The menu of available options should", () => {
    test("include an item with aria-current, as set by the `current` prop.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <AdjustableHeadCell
            options={new RsSet(["foo", "bar", "baz"])}
            current="foo"
            onChange={() => {}}
            sortableProperties={[]}
          />
        </ThemeProvider>
      );

      fireEvent.click(screen.getByRole("button", { name: "Column options" }));
      expect(screen.getByRole("menuitem", { name: "foo" })).toHaveAttribute(
        "aria-current",
        "true"
      );
    });
  });
});
