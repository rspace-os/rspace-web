import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { TYPE_LABEL } from "../../../../stores/definitions/Search";
import materialTheme from "../../../../theme";
import ToggleView from "../ToggleView";

describe("ToggleView", () => {
  test("Current view should have aria-current property", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ToggleView
          currentView={(Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]}
          views={Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>}
          onChange={() => Promise.resolve()}
        />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "inventory:search.controls.view.changeView" }));
    expect(
      screen.getByRole("menuitem", {
        name: "inventory:search.controls.view.list",
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
