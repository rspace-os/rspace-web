import CloseIcon from "@mui/icons-material/Close";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../theme";
import IconButtonWithTooltip from "../IconButtonWithTooltip";

describe("IconButtonWithTooltip", () => {
  test("Renders title and aria-label attributes.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <IconButtonWithTooltip title="foo" icon={<CloseIcon />} />
      </ThemeProvider>,
    );
    screen.getByLabelText("foo");
  });
  test("onClick functions correctly.", async () => {
    const user = userEvent.setup();

    const onClick = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <IconButtonWithTooltip title="foo" icon={<CloseIcon />} onClick={onClick} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
  });
});
