import { describe, expect, it } from "vitest";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { openingWindowsOnDate } from "./DayTimelineEvent";

const tokyo = {
  timezone: "Asia/Tokyo",
  openingStart: "10:00",
  openingEnd: "16:00",
  openDays: ALL_ISO_WEEKDAYS,
  openingExceptions: [],
};

describe("openingWindowsOnDate", () => {
  it.each([
    ["Europe/Berlin", [{ start: "03:00 AM", end: "09:00 AM" }]],
    ["Pacific/Honolulu", [{ start: "03:00 PM", end: "09:00 PM" }]],
    ["America/Los_Angeles", [{ start: "06:00 PM", end: "12:00 AM (+1)" }]],
    ["Asia/Tokyo", [{ start: "10:00 AM", end: "04:00 PM" }]],
  ])("shows Tokyo opening hours in %s", (displayTimezone, expected) => {
    expect(openingWindowsOnDate("2026-09-25", displayTimezone, tokyo)).toEqual(expected);
  });

  it("follows the viewer's clock change", () => {
    expect(openingWindowsOnDate("2026-10-26", "Europe/Berlin", tokyo)).toEqual([
      { start: "02:00 AM", end: "08:00 AM" },
    ]);
  });

  it("lists both instrument windows that intersect one viewer day", () => {
    // Tokyo is 16 hours ahead of Los Angeles, so Friday and Saturday in Tokyo both reach Friday in Los Angeles.
    expect(
      openingWindowsOnDate("2026-09-25", "America/Los_Angeles", {
        ...tokyo,
        openingStart: "09:00",
        openingEnd: "17:00",
      }),
    ).toEqual([
      { start: "12:00 AM", end: "01:00 AM" },
      { start: "05:00 PM", end: "12:00 AM (+1)" },
    ]);
  });

  it("uses the instrument weekday, so a viewer Friday can be closed by an instrument Saturday closure", () => {
    // Tokyo Friday 10:00–16:00 is Thursday evening in Los Angeles; Friday there meets only Tokyo's Saturday.
    expect(
      openingWindowsOnDate("2026-09-25", "America/Los_Angeles", { ...tokyo, openDays: [1, 2, 3, 4, 5, 7] }),
    ).toEqual([]);
  });

  it("uses a weekday exception's hours", () => {
    expect(
      openingWindowsOnDate("2026-09-25", "Asia/Tokyo", {
        ...tokyo,
        openingExceptions: [{ dayOfWeek: 5, start: "12:00", end: "13:00" }],
      }),
    ).toEqual([{ start: "12:00 PM", end: "01:00 PM" }]);
  });
});
