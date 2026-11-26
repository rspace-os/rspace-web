import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import { TYPE_LABEL } from "../../../../stores/definitions/Search";
import materialTheme from "../../../../theme";
import ToggleView from "../ToggleView";

describe("ToggleView", () => {
  test("Current view should have aria-current property", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <ToggleView
          currentView={(Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]}
          views={Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>}
          onChange={() => Promise.resolve()}
        />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "inventory:search.controls.view.changeView" }));
    expect(
      screen.getByRole("menuitem", {
        name: "inventory:search.controls.view.list",
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
