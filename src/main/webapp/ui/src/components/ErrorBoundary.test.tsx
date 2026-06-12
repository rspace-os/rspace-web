import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import ErrorBoundary from "./ErrorBoundary";
import { ErrorComponent } from "./ErrorBoundary.story";

/*
 * Converted from ErrorBoundary.spec.tsx (Playwright CT). This component only
 * renders text/markup once it catches a child render error, so it gets no value
 * from a real browser and runs as a jsdom unit test instead.
 */
describe("ErrorBoundary", () => {
  // React logs the caught render error via console.error; that noise is
  // expected here and would otherwise clutter the test output.
  let restoreConsole: () => void;
  beforeEach(() => {
    restoreConsole = silenceConsole(["error"], [/.*/]);
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
});
