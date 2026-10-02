import { describe, expect, it, vi } from "vitest";
import { nextFreeRange, scrollCalendarWithArrowKeys } from "../calendarLayoutUtils";

describe("nextFreeRange", () => {
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
