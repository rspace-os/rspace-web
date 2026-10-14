import { describe, expect, it } from "vitest";
import { nextFreeRange } from "../calendarLayoutUtils";

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
