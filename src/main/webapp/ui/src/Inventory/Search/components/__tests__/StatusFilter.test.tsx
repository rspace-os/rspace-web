import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import materialTheme from "../../../../theme";
import StatusFilter from "../StatusFilter";

describe("StatusFilter", () => {
  test("Current status should have aria-current property", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <StatusFilter current="EXCLUDE" onClose={() => {}} anchorEl={document.createElement("div")} />
      </ThemeProvider>,
    );
    expect(
      screen.getByRole("menuitem", {
        name: "Current",
      }),
    ).toHaveAttribute("aria-current", "true");
  });
});
