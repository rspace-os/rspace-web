import { describe, expect, it } from "vitest";
import { validCalendarDate } from "./calendarDate";

describe("booking calendar dates", () => {
  it("rejects malformed and impossible dates", () => {
    expect(validCalendarDate("2026-08-17")).toBe(true);
    expect(validCalendarDate("2026-02-31")).toBe(false);
    expect(validCalendarDate("17-08-2026")).toBe(false);
  });
});
