import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import CloseIcon from "@mui/icons-material/Close";
import IconButtonWithTooltip from "../IconButtonWithTooltip";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import userEvent from "@testing-library/user-event";




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
    const onClick = vi.fn();

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


