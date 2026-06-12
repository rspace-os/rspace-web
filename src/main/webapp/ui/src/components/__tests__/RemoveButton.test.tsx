import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../theme";
import RemoveButton from "../RemoveButton";

describe("RemoveButton", () => {
  test("Should invoke onClick when clicked.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <RemoveButton onClick={onClick} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
  });
  test('Has default tooltip "Delete"', () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RemoveButton />
      </ThemeProvider>,
    );
    screen.getByLabelText("Delete");
  });
});
