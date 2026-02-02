import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import useViewportDimensions from "../useViewportDimensions";




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


