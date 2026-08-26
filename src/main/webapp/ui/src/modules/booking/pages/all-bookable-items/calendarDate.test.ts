import { describe, expect, it } from "vitest";
import { addCalendarDays, validCalendarDate } from "./calendarDate";

describe("booking calendar dates", () => {
  it("moves across month boundaries", () => {
    expect(addCalendarDays("2026-08-31", 1)).toBe("2026-09-01");
  });

  it("rejects malformed and impossible dates", () => {
    expect(validCalendarDate("2026-08-17")).toBe(true);
    expect(validCalendarDate("2026-02-31")).toBe(false);
    expect(validCalendarDate("17-08-2026")).toBe(false);
  });
});
