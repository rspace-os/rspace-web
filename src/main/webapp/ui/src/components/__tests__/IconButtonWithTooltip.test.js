/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import CloseIcon from "@mui/icons-material/Close";
import IconButtonWithTooltip from "../IconButtonWithTooltip";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("IconButtonWithTooltip", () => {
  test("Renders title and aria-label attributes.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IconButtonWithTooltip title="foo" icon={<CloseIcon />} />
      </ThemeProvider>
    );

    screen.getByLabelText("foo");
  });
  test("onClick functions correctly.", async () => {
    const user = userEvent.setup();
    const onClick = jest.fn<[Event], void>();

    render(
      <ThemeProvider theme={materialTheme}>
        <IconButtonWithTooltip
          title="foo"
          icon={<CloseIcon />}
          onClick={onClick}
        />
      </ThemeProvider>
    );

    await user.click(screen.getByRole("button"));

    expect(onClick).toHaveBeenCalled();
  });
});
