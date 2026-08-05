import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import ErrorBoundary, { MessageBoundary } from "../ErrorBoundary";
import { ErrorComponent } from "../ErrorBoundary.story";

describe("ErrorBoundary", () => {
  let restoreConsole: () => void;
  beforeEach(() => {
    // React logs the caught render error via console.error; that noise is
    // expected here (and in the intentionally-throwing MessageBoundary case
    // below) and would otherwise clutter the test output.
    restoreConsole = silenceConsole(
      ["error"],
      ["Error: Error", "ErrorComponent", "message render failed", "ThrowingMessage"],
    );
  });
  afterEach(() => {
    restoreConsole();
  });

  test("When there is an error rendering one of its descendent components, ErrorBoundary should show an error message.", () => {
    render(
      <ErrorBoundary message="Something went wrong.">
        <ErrorComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByText("Something went wrong.")).toBeVisible();
  });

  test("When rendering the error message itself throws, MessageBoundary falls back to plain untranslated text.", () => {
    const ThrowingMessage = (): never => {
      throw new Error("message render failed");
    };
    render(
      <MessageBoundary>
        <ThrowingMessage />
      </MessageBoundary>,
    );
    expect(screen.getByText(/Something went wrong! Please refresh the page/)).toBeVisible();
  });
});
