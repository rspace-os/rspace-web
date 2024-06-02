/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import useViewportDimensions from "../useViewportDimensions";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

function Wrapper() {
  const { width } = useViewportDimensions();
  return <>{width}</>;
}

describe("useViewportDimensions", () => {
  test("Inside of a test environment, width defaults to 1024px.", () => {
    render(<Wrapper />);
    expect(screen.getByText("1024")).toBeVisible();
  });
});
