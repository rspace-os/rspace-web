import { test, describe, expect, vi } from 'vitest';
import React from "react";
import { render, screen } from "@testing-library/react";
import RemoveButton from "../RemoveButton";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
import userEvent from "@testing-library/user-event";
describe("RemoveButton", () => {
  test("Should invoke onClick when clicked.", async () => {
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
  test('Has default tooltip "Delete"', () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RemoveButton />
      </ThemeProvider>
    );
    screen.getByLabelText("Delete");
  });
});

