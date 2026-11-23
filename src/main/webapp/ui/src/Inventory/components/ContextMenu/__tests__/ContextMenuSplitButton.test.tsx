import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import materialTheme from "../../../../theme";
import ContextMenuSplitButton from "../ContextMenuSplitButton";

describe("ContextMenuSplitButton", () => {
  test("Current view should have aria-current property", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ContextMenuSplitButton options={[{ text: "foo" }]} icon={null} />
      </ThemeProvider>,
    );
    // none of the options are selected until one has been used
    // so first we tap one of the options
    await user.click(screen.getByRole("button", { name: "inventory:contextMenu.splitButton.moreOptions" }));

    await user.click(screen.getByRole("menuitem", { name: "foo" }));
    // then we reopen the menu to assert the now selected option
    await user.click(screen.getByRole("button", { name: "inventory:contextMenu.splitButton.moreOptions" }));
    expect(
      screen.getByRole("menuitem", {
        name: "foo",
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
