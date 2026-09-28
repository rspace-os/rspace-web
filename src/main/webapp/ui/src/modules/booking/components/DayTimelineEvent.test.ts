import { describe, expect, it } from "vitest";
import { formatDayDate, formatMinute, formatMinuteWithDayOffset } from "./DayTimelineEvent";

describe("DayTimelineEvent formatting", () => {
  it("writes visible dates and times in the app locale", () => {
    expect(formatDayDate("2026-08-17")).toBe("08/17/2026");
    expect(formatMinuteWithDayOffset("2026-08-17", "UTC", 14 * 60 + 5)).toBe("02:05 PM");
  });

  it("keeps the day offset and the clock-change offset", () => {
    expect(formatMinuteWithDayOffset("2026-08-17", "UTC", 25 * 60)).toBe("01:00 AM (+1)");
    expect(formatMinuteWithDayOffset("2026-10-25", "Europe/Berlin", 165)).toBe("02:45 AM +02:00");
    expect(formatMinuteWithDayOffset("2026-10-25", "Europe/Berlin", 225)).toBe("02:45 AM +01:00");
  });

  it("keeps machine-readable times for dateTime attributes", () => {
    expect(formatMinute("2026-08-17", "UTC", 14 * 60 + 5)).toBe("14:05");
  });
});
