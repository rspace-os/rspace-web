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
import RemoveButton from "../RemoveButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("RemoveButton", () => {
  it("Should invoke onClick when clicked.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <RemoveButton onClick={onClick} />
      </ThemeProvider>
    );

    await user.click(screen.getByRole("button"));

    expect(onClick).toHaveBeenCalled();
  });

  it('Has default tooltip "Delete"', () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RemoveButton />
      </ThemeProvider>
    );

    screen.getByLabelText("Delete");
  });
});


