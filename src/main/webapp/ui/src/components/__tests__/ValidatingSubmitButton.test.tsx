/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import ValidatingSubmitButton from "../ValidatingSubmitButton";
import Result from "../../util/result";
import "../../../__mocks__/matchMedia";
import each from "jest-each";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ValidatingSubmitButton", () => {
  test("When validationResult is OK and the button is tapped, onClick should be called.", () => {
    const onClick = jest.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Ok(null)}
          onClick={onClick}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>
    );
    fireEvent.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
  });
  test("When validationResult is Error and the button is tapped, onClick should not be called.", () => {
    const onClick = jest.fn();
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Error<null>([new Error("test")])}
          onClick={onClick}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>
    );
    fireEvent.click(screen.getByRole("button"));
    expect(onClick).not.toHaveBeenCalled();
  });
  test("When validationResult is Error and the button is shown, the errors should be displayed.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Error<null>([new Error("test")])}
          onClick={() => {}}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>
    );
    fireEvent.click(screen.getByRole("button"));
    const alert = screen.getByRole("alert", { name: "Warning" });
    expect(alert).toBeVisible();
    expect(alert).toHaveTextContent("test");
  });
  each([Result.Ok(null), Result.Error<null>([new Error("test")])]).test(
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
        </ThemeProvider>
      );
    }
  );
});
