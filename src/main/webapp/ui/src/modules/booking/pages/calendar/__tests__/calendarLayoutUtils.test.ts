import { describe, expect, it, vi } from "vitest";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { ownBooking } from "../../mocks/bookingCalendarFixtures";
import { nextFreeRange, scrollCalendarWithArrowKeys, shiftDate, startOfWeek } from "../calendarLayoutUtils";

describe("calendar date layout", () => {
  it("keeps a retained booking while omitting its redacted target", () => {
    const event = toTimelineEvent(
      { ...ownBooking, target: null, timezone: null, canEdit: false, canViewConfiguration: false },
      "2026-08-17",
      "UTC",
    );

    expect(event).toMatchObject({
      kind: "booking",
      privacy: "full",
      title: "common:values.unknownItem",
      item: { name: "common:values.unknownItem", globalId: null },
      canEdit: false,
    });
  });

  it("starts weeks on Monday using the ISO calendar date", () => {
    expect(startOfWeek("2026-03-29")).toBe("2026-03-23");
    expect(startOfWeek("2026-03-30")).toBe("2026-03-30");
  });

  it("shifts months from the first day across year boundaries", () => {
    expect(shiftDate("2026-03-29", "month", -1)).toBe("2026-02-01");
    expect(shiftDate("2026-01-31", "month", -1)).toBe("2025-12-01");
  });
});

describe("nextFreeRange", () => {
  it("fits whole increments to a 30-minute opening", () => {
    const closed = [
      { startMinute: 0, endMinute: 10 * 60 },
      { startMinute: 10 * 60 + 30, endMinute: 24 * 60 },
    ];
    expect(nextFreeRange([], "2026-10-05", "UTC", 8 * 60, 18 * 60, 15, closed)).toEqual({
      startMinute: 10 * 60,
      endMinute: 10 * 60 + 30,
    });
  });

  it("rounds the preferred duration up to whole increments", () => {
    expect(nextFreeRange([], "2026-10-05", "UTC", 600, 1080, 45)).toEqual({
      startMinute: 630,
      endMinute: 720,
    });
    expect(nextFreeRange([], "2026-10-05", "UTC", 600, 1080, 120)).toEqual({
      startMinute: 600,
      endMinute: 720,
    });
  });

  it("does not propose a slot on a wholly closed day", () => {
    expect(
      nextFreeRange([], "2026-10-05", "UTC", 0, 24 * 60, 15, [{ startMinute: 0, endMinute: 24 * 60 }]),
    ).toBeUndefined();
  });

  it("skips unavailable ranges such as closed hours", () => {
    const closed = [
      { startMinute: 0, endMinute: 10 * 60 },
      { startMinute: 16 * 60, endMinute: 24 * 60 },
    ];
    expect(nextFreeRange([], "2026-10-05", "Europe/Berlin", 9 * 60, 19 * 60, 15, closed)).toEqual({
      startMinute: 10 * 60,
      endMinute: 11 * 60,
    });
    expect(nextFreeRange([], "2026-10-05", "Europe/Berlin", 17 * 60, 19 * 60, 15, closed)).toBeUndefined();
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
    const keyEvent = (overrides: Partial<KeyboardEvent> = {}) => ({
      key: "ArrowRight",
      target: region,
      currentTarget: region,
      defaultPrevented: false,
      preventDefault,
      ...overrides,
    });

    for (const event of [keyEvent({ key: "ArrowLeft", target: child }), keyEvent({ defaultPrevented: true })]) {
      scrollCalendarWithArrowKeys(event as unknown as Parameters<typeof scrollCalendarWithArrowKeys>[0]);
    }

    expect(preventDefault).not.toHaveBeenCalled();
    expect(scrollBy).not.toHaveBeenCalled();
  });
});
