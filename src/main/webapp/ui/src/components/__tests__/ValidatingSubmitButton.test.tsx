import { test, describe, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import ValidatingSubmitButton from "../ValidatingSubmitButton";
import Result from "../../util/result";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import materialTheme from "../../theme";

const renderButton = ({
  loading = false,
  validationResult = Result.Ok(null),
  onClick = () => {},
  children = "Click me",
  progress,
}: {
  loading?: boolean;
  validationResult?: Result<null>;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  children?: React.ReactNode;
  progress?: number;
} = {}) =>
  render(
    <ThemeProvider theme={materialTheme}>
      <ValidatingSubmitButton
        loading={loading}
        validationResult={validationResult}
        onClick={onClick}
        {...(progress !== undefined ? { progress } : {})}
      >
        {children}
      </ValidatingSubmitButton>
    </ThemeProvider>
  );

describe("ValidatingSubmitButton", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent() {
          return false;
        },
      })),
    );
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test("When validationResult is OK and the button is tapped, onClick should be called.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    renderButton({ onClick });
    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalled();
  });

  test("When validationResult is Error and the button is tapped, onClick should not be called.", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    renderButton({
      validationResult: Result.Error<null>([new Error("test")]),
      onClick,
    });
    await user.click(screen.getByRole("button"));
    expect(onClick).not.toHaveBeenCalled();
  });

  test("When validationResult is Error and the button is shown, the errors should be displayed.", async () => {
    const user = userEvent.setup();
    renderButton({
      validationResult: Result.Error<null>([new Error("test")]),
    });
    await user.click(screen.getByRole("button"));
    const alert = screen.getByRole("alert", { name: "Warning" });
    expect(alert).toBeVisible();
    expect(alert).toHaveTextContent("test");
  });

  test.each([Result.Ok(null), Result.Error<null>([new Error("test")])])(
    "When loading is true and validationResult is %s, the button should be disabled.",
    (validationResult: Result<null>) => {
      renderButton({ loading: true, validationResult });
      expect(screen.getByRole("button")).toBeDisabled();
    }
  );

  test("Shows the expected label and submit type.", () => {
    renderButton({ children: "Submit" });
    const button = screen.getByRole("button", { name: "Submit" });
    expect(button).toHaveTextContent("Submit");
    expect(button).toHaveAttribute("type", "submit");
  });

  test("Does not show the validation dialog after a valid click.", async () => {
    const user = userEvent.setup();
    renderButton();
    await user.click(screen.getByRole("button"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  test("Shows and then clears the progress indicator.", async () => {
    const { rerender } = renderButton({ progress: 10 });

    expect(screen.getByRole("progressbar")).toBeVisible();

    rerender(
      <ThemeProvider theme={materialTheme}>
        <ValidatingSubmitButton
          loading={false}
          validationResult={Result.Ok(null)}
          onClick={() => {}}
        >
          Click me
        </ValidatingSubmitButton>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    });
  });

  test("Is accessible in the default state.", async () => {
    const { baseElement } = renderButton({ children: "Submit" });
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });

  test("Shows the warning alert with the expected aria-label.", async () => {
    const user = userEvent.setup();
    renderButton({
      validationResult: Result.Error<null>([new Error("Validation failed.")]),
      children: "Submit",
    });
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(screen.getByRole("alert", { name: "Warning" })).toBeVisible();
  });

  test("Is enabled when not loading.", () => {
    renderButton();
    expect(screen.getByRole("button")).toBeEnabled();
  });
});
