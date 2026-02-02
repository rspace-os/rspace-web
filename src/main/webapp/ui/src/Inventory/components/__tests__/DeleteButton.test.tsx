import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
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

  it("Shows disabled tooltip.", () => {
    renderDeleteButton({ disabled: true });
    expect(screen.getByLabelText("baz"));
  });

  it("Shows before clicked tooltip.", () => {
    renderDeleteButton();
    expect(screen.getByLabelText("bar"));
  });

  it("Shows after clicked tooltip.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn(() => {});
    renderDeleteButton({ onClick });
    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
    expect(screen.getByLabelText("foo"));
  });

  it("Becomes disabled once clicked.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn(() => {});
    renderDeleteButton({ onClick });

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button")).toBeDisabled();
  });
});


