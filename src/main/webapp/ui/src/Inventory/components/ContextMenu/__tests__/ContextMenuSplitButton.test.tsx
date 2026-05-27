import userEvent from "@testing-library/user-event";
import { test, describe, expect } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import ContextMenuSplitButton from "../ContextMenuSplitButton";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
describe("ContextMenuSplitButton", () => {
  test("Current view should have aria-current property", async () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ContextMenuSplitButton
          options={[
            {
              text: "foo",
            },
          ]}
          icon={null}
        />
      </ThemeProvider>,
    );
    // none of the options are selected until one has been used
    // so first we tap one of the options
    await userEvent.click(
      screen.getByRole("button", {
        name: "More selection options",
      }),
    );
    await userEvent.click(
      screen.getByRole("menuitem", {
        name: "foo",
      }),
    );
    // then we reopen the menu to assert the now selected option
    await userEvent.click(
      screen.getByRole("button", {
        name: "More selection options",
      }),
    );
    expect(
      screen.getByRole("menuitem", {
        name: "foo",
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
