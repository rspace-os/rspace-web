import { Temporal } from "@js-temporal/polyfill";
import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { elapsedNowMinute, pastWallClockMinutes, useBookingNow } from "../useBookingNow";

afterEach(() => vi.useRealTimers());
describe("booking clock", () => {
  it("shares a stable snapshot, aligns to the minute and crosses midnight", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-17T23:59:45Z"));
    const first = renderHook(() => useBookingNow());
    const second = renderHook(() => useBookingNow());
    const before = first.result.current;
    expect(second.result.current).toBe(before);
    act(() => vi.advanceTimersByTime(14999));
    expect(first.result.current).toBe(before);
    act(() => vi.advanceTimersByTime(1));
    expect(first.result.current.epochMilliseconds).toBe(Date.parse("2026-08-18T00:00:00Z"));
    expect(second.result.current).toBe(first.result.current);
    first.unmount();
    second.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
  it.each([
    ["2026-03-29T01:30:00Z", "2026-03-29", "Europe/Berlin", 150, 210],
    ["2026-10-25T02:30:00Z", "2026-10-25", "Europe/Berlin", 270, 210],
    ["2026-08-17T00:00:00Z", "2026-08-17", "America/New_York", -240, 0],
    ["2026-08-18T00:00:00Z", "2026-08-17", "UTC", 1440, 1440],
  ])("converts %s on %s in %s", (instant, date, zone, elapsed, wall) => {
    const now = Temporal.Instant.from(instant);
    expect(elapsedNowMinute(now, date, zone)).toBe(elapsed);
    expect(pastWallClockMinutes(now, date, zone)).toBe(wall);
  });
});
