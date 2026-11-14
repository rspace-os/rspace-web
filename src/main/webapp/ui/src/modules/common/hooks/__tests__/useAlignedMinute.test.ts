import { act, renderHook } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { useAlignedMinute } from "../useAlignedMinute";

afterEach(() => vi.useRealTimers());

test("ticks first at the next minute boundary and continues each minute", () => {
  vi.useFakeTimers();
  const start = Date.parse("2026-10-29T12:34:42.250Z");
  vi.setSystemTime(start);
  const { result, unmount } = renderHook(() => useAlignedMinute());
  expect(result.current).toBe(start - 42_250);
  act(() => vi.advanceTimersByTime(17_749));
  expect(result.current).toBe(start - 42_250);
  act(() => vi.advanceTimersByTime(1));
  expect(result.current).toBe(start + 17_750);
  act(() => vi.advanceTimersByTime(60_000));
  expect(result.current).toBe(start + 77_750);
  unmount();
  expect(vi.getTimerCount()).toBe(0);
});
