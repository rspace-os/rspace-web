import { describe, expect, it, vi } from "vitest";
import { timelinePolicy } from "@/modules/booking/creation/timelineRange";
import type { IncrementUnit } from "@/modules/booking/domain/bookingIncrement";
import { nextFreeRange, scrollCalendarWithArrowKeys } from "../calendarLayoutUtils";

function policy(
  slotGranularityMinutes: number,
  { timezone = "UTC", incrementUnit = "MINUTES" as IncrementUnit, alignToIncrement = true } = {},
) {
  return timelinePolicy({
    timezone,
    slotGranularityMinutes,
    incrementUnit,
    alignToIncrement,
    openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

    maxBookingDurationMinutes: 0,
  });
}

describe("nextFreeRange", () => {
  it("proposes a whole number of increments starting on the grid", () => {
    expect(nextFreeRange([], "2026-10-05", "UTC", 600, 1080, policy(120))).toEqual({
      startMinute: 600,
      endMinute: 720,
    });
    expect(nextFreeRange([], "2026-10-05", "UTC", 600, 1080, policy(45))).toEqual({ startMinute: 630, endMinute: 720 });
  });
  it("starts an unaligned proposal at the window start", () => {
    expect(nextFreeRange([], "2026-10-05", "UTC", 607, 1080, policy(45, { alignToIncrement: false }))).toEqual({
      startMinute: 607,
      endMinute: 697,
    });
  });
  it("extends a Day proposal past the displayed day at the same local time", () => {
    // 5 October 2026 has 24 hours in UTC, so a two-day proposal is 2880 elapsed minutes.
    expect(
      nextFreeRange(
        [],
        "2026-10-05",
        "UTC",
        600,
        1080,
        policy(2880, { incrementUnit: "DAYS", alignToIncrement: false }),
      ),
    ).toEqual({
      startMinute: 600,
      endMinute: 600 + 2880,
    });
  });
  it("skips unavailable ranges such as closed hours", () => {
    const closed = [
      { startMinute: 0, endMinute: 10 * 60 },
      { startMinute: 16 * 60, endMinute: 24 * 60 },
    ];
    expect(
      nextFreeRange(
        [],
        "2026-10-05",
        "Europe/Berlin",
        9 * 60,
        19 * 60,
        policy(15, { timezone: "Europe/Berlin" }),
        closed,
      ),
    ).toEqual({
      startMinute: 10 * 60,
      endMinute: 11 * 60,
    });
    expect(
      nextFreeRange(
        [],
        "2026-10-05",
        "Europe/Berlin",
        17 * 60,
        19 * 60,
        policy(15, { timezone: "Europe/Berlin" }),
        closed,
      ),
    ).toBeUndefined();
  });
});

describe("scrollCalendarWithArrowKeys", () => {
  it("scrolls only when the calendar region itself owns the event", () => {
    const region = document.createElement("section");
    const scrollBy = vi.fn();
    Object.defineProperty(region, "scrollBy", { value: scrollBy });
    const preventDefault = vi.fn();
    const event = {
      key: "ArrowRight",
      target: region,
      currentTarget: region,
      defaultPrevented: false,
      preventDefault,
    } as unknown as Parameters<typeof scrollCalendarWithArrowKeys>[0];

    scrollCalendarWithArrowKeys(event);

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(scrollBy).toHaveBeenCalledWith({ left: 240, behavior: "smooth" });
  });

  it("leaves bubbled or already handled arrow keys alone", () => {
    const region = document.createElement("section");
    const child = document.createElement("button");
    region.append(child);
    const scrollBy = vi.fn();
    Object.defineProperty(region, "scrollBy", { value: scrollBy });
    const preventDefault = vi.fn();

    for (const event of [
      {
        key: "ArrowLeft",
        target: child,
        currentTarget: region,
        defaultPrevented: false,
        preventDefault,
      },
      {
        key: "ArrowRight",
        target: region,
        currentTarget: region,
        defaultPrevented: true,
        preventDefault,
      },
    ]) {
      scrollCalendarWithArrowKeys(event as unknown as Parameters<typeof scrollCalendarWithArrowKeys>[0]);
    }

    expect(preventDefault).not.toHaveBeenCalled();
    expect(scrollBy).not.toHaveBeenCalled();
  });
});
