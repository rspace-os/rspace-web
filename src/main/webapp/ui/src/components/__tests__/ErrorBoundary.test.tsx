import { test, describe, expect } from "vitest";
import React from "react";
import { render } from "@testing-library/react";
import ErrorBoundary from "../ErrorBoundary";
import { ErrorComponent } from "../ErrorBoundary.story";

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";

function AlwaysError(): React.ReactNode {
  throw new Error("foo");
}

function withSilencedErrors(assertions: () => void) {
  const restoreConsole = silenceConsole(["error"], [/./]);
  const errorHandler = (event: ErrorEvent) => {
    event.preventDefault();
  };
  window.addEventListener("error", errorHandler);
  try {
    assertions();
  } finally {
    window.removeEventListener("error", errorHandler);
    restoreConsole();
  }
}

describe("ErrorBoundary", () => {
  test("Reports the support email address.", () => {
    withSilencedErrors(() => {
      const { container } = render(
        <ErrorBoundary>
          <AlwaysError />
        </ErrorBoundary>
      );
      expect(container).toHaveTextContent("support@researchspace.com");
    });
  });

  test("Shows a custom fallback message when a child throws.", () => {
    withSilencedErrors(() => {
      const { container } = render(
        <ErrorBoundary message="Something went wrong.">
          <ErrorComponent />
        </ErrorBoundary>
      );
      expect(container).toHaveTextContent("Something went wrong.");
    });
  });
});
