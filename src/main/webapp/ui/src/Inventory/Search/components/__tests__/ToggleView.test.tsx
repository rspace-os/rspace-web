import userEvent from "@testing-library/user-event";
import { test, describe, expect } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import ToggleView from "../ToggleView";
import { TYPE_LABEL } from "../../../../stores/definitions/Search";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
describe("ToggleView", () => {
  test("Current view should have aria-current property", async () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ToggleView
          currentView={
            (Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]
          }
          views={Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>}
          onChange={() => Promise.resolve()}
        />
      </ThemeProvider>,
    );
    await userEvent.click(
      screen.getByRole("button", {
        name: "Change view",
      }),
    );
    expect(
      screen.getByRole("menuitem", {
        name: TYPE_LABEL[
          (Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]
        ],
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
