/*
 */
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
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import ErrorBoundary from "../ErrorBoundary";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";

beforeEach(() => {
  vi.clearAllMocks();
});


function AlwaysError(): React.ReactNode {
  throw new Error("foo");
}

describe("ErrorBoundary", () => {
  it("Reports the support email address.", () => {
    /*
     * This is needed because the `render` function will report any errors
     * using console.error, even though the ErrorBoundary catches them, which
     * just pollutes the output of the vi CLI runner
     */
     
    const restoreConsole = silenceConsole(["error"], [/./]);
    const errorHandler = (event: ErrorEvent) => {
      event.preventDefault();
    };
    window.addEventListener("error", errorHandler);

    try {
      const { container } = render(
        <ErrorBoundary>
          <AlwaysError />
        </ErrorBoundary>
      );

      expect(container).toHaveTextContent("support@researchspace.com");
    } finally {
      window.removeEventListener("error", errorHandler);
      restoreConsole();
    }
  });
});
