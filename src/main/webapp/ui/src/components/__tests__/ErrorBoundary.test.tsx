import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import ErrorBoundary from "../ErrorBoundary";
import { ErrorComponent } from "../ErrorBoundary.story";

describe("ErrorBoundary", () => {
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
