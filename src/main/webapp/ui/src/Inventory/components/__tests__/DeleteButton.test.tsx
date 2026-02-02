import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import DeleteButton from "../DeleteButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("DeleteButton", () => {
  function renderDeleteButton(props?: {
    disabled?: boolean;
    onClick?: () => void;
  }) {
    return render(
      <ThemeProvider theme={materialTheme}>
        <DeleteButton
          onClick={() => {}}
          disabled={false}
          tooltipAfterClicked="foo"
          tooltipBeforeClicked="bar"
          tooltipWhenDisabled="baz"
          {...props}
        />
      </ThemeProvider>
    );
  }

  test("Shows disabled tooltip.", () => {
    renderDeleteButton({ disabled: true });
    expect(screen.getByLabelText("baz"));
  });

  test("Shows before clicked tooltip.", () => {
    renderDeleteButton();
    expect(screen.getByLabelText("bar"));
  });

  test("Shows after clicked tooltip.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn(() => {});
    renderDeleteButton({ onClick });
    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
    expect(screen.getByLabelText("foo"));
  });

  test("Becomes disabled once clicked.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn(() => {});
    renderDeleteButton({ onClick });

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button")).toBeDisabled();
  });
});


