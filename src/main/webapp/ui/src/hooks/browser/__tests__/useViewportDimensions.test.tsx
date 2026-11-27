import { act, render, renderHook, screen } from "@testing-library/react";
import type React from "react";
import { afterEach, describe, expect, test } from "vitest";

import useViewportDimensions from "../useViewportDimensions";

const initialWidth = window.innerWidth;

function resizeTo(width: number) {
  act(() => {
    Object.defineProperty(window, "innerWidth", { configurable: true, value: width });
    window.dispatchEvent(new Event("resize"));
  });
}

describe("useViewportDimensions", () => {
  afterEach(() => {
    resizeTo(initialWidth);
  });

  test("Inside of a test environment, the 1024px wide viewport is medium-sized.", () => {
    const { result } = renderHook(() => useViewportDimensions());
    expect(result.current).toEqual({
      viewportSize: "md",
      isViewportVerySmall: false,
      isViewportSmall: false,
      isViewportLarge: false,
    });
  });

  test("Resizing across a breakpoint updates the component.", () => {
    function Consumer(): React.ReactNode {
      const { isViewportSmall } = useViewportDimensions();
      return <>{isViewportSmall ? "small" : "not small"}</>;
    }
    render(<Consumer />);
    expect(screen.getByText("not small")).toBeInTheDocument();

    resizeTo(500);
    expect(screen.getByText("small")).toBeInTheDocument();

    resizeTo(1300);
    expect(screen.getByText("not small")).toBeInTheDocument();
  });

  test("Resizing within a breakpoint neither re-renders nor changes the returned object.", () => {
    let renders = 0;
    const { result } = renderHook(() => {
      renders++;
      return useViewportDimensions();
    });
    const before = result.current;
    const rendersBefore = renders;

    resizeTo(1000);
    resizeTo(800);

    expect(renders).toBe(rendersBefore);
    expect(result.current).toBe(before);
  });
});
