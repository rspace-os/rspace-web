/*
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import useViewportDimensions from "../useViewportDimensions";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

function Wrapper(): React.ReactNode {
  const { width } = useViewportDimensions();
  return <>{width}</>;
}

describe("useViewportDimensions", () => {
  test("Inside of a test environment, width defaults to 1024px.", () => {
    render(<Wrapper />);
    expect(screen.getByText("1024")).toBeVisible();
  });
});


