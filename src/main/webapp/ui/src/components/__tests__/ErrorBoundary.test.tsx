/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import ErrorBoundary from "../ErrorBoundary";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function AlwaysError(): React.ReactNode {
  throw new Error("foo");
}

describe("ErrorBoundary", () => {
  test("Reports the support email address.", () => {
    /*
     * This is needed because the `render` function will report any errors
     * using console.error, even though the ErrorBoundary catches them, which
     * just pollutes the output of the jest CLI runner
     */
    // eslint-disable-next-line no-undef
    jest.spyOn(global.console, "error").mockImplementation(() => {});

    const { container } = render(
      <ErrorBoundary>
        <AlwaysError />
      </ErrorBoundary>
    );

    expect(container).toHaveTextContent("support@researchspace.com");
  });
});
