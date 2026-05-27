import userEvent from "@testing-library/user-event";
import { test, describe, expect, vi } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import ValidatingSubmitButton from "../ValidatingSubmitButton";
import Result from "../../util/result";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";
describe("ValidatingSubmitButton", () => {
  test("When validationResult is OK and the button is tapped, onClick should be called.", async () => {
    const onClick = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Ok(null)}
          onClick={onClick}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
  });
  test("When validationResult is Error and the button is tapped, onClick should not be called.", async () => {
    const onClick = vi.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Error<null>([new Error("test")])}
          onClick={onClick}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).not.toHaveBeenCalled();
  });
  test("When validationResult is Error and the button is shown, the errors should be displayed.", async () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Error<null>([new Error("test")])}
          onClick={() => {}}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>,
    );
    await userEvent.click(screen.getByRole("button"));
    const alert = screen.getByRole("alert", {
      name: "Warning",
    });
    expect(alert).toBeVisible();
    expect(alert).toHaveTextContent("test");
  });
  test.each([Result.Ok(null), Result.Error<null>([new Error("test")])])(
    "When loading is true and validationResult is %s, the button should be disabled.",
    (validationResult: Result<null>) => {
      render(
        <ThemeProvider theme={materialTheme}>
          <ValidatingSubmitButton
            loading={true}
            validationResult={validationResult}
            onClick={() => {}}
          >
            Click me
          </ValidatingSubmitButton>
        </ThemeProvider>,
      );
    },
  );
});
