import { describe, expect, it } from "vitest";
import { monthDayCount, shiftDate, startOfWeek } from "../calendarLayoutUtils";

describe("calendar date layout", () => {
  it("starts weeks on Monday using the ISO calendar date", () => {
    expect(startOfWeek("2026-03-29")).toBe("2026-03-23");
    expect(startOfWeek("2026-03-30")).toBe("2026-03-30");
  });

  it("uses the number of days in the ISO month", () => {
    expect(monthDayCount("2024-02-15")).toBe(29);
    expect(monthDayCount("2025-02-15")).toBe(28);
  });

  it("shifts months from the first day across year boundaries", () => {
    expect(shiftDate("2026-03-29", "month", -1)).toBe("2026-02-01");
    expect(shiftDate("2026-01-31", "month", -1)).toBe("2025-12-01");
  });
});
