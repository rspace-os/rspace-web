import { describe, expect, it } from "vitest";
import { weeklyOpeningHours } from "@/modules/booking/configuration/openingHoursFacts";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";

const berlin = {
  timezone: "Europe/Berlin",
  openingStart: "08:00",
  openingEnd: "17:00",
  openDays: [...ALL_ISO_WEEKDAYS],
  openingExceptions: [],
};
const hours = (days: ReturnType<typeof weeklyOpeningHours>) => days.map((day) => day.hours);

describe("weeklyOpeningHours", () => {
  it("converts each weekday into the viewer's timezone", () => {
    expect(hours(weeklyOpeningHours(berlin, "America/New_York", "2026-10-07"))).toEqual(
      Array(7).fill("02:00 AM - 11:00 AM"),
    );
  });

  it("marks a boundary on the next or previous date with (+1) or (-1)", () => {
    expect(weeklyOpeningHours(berlin, "Pacific/Auckland", "2026-10-07")[0]?.hours).toBe("07:00 PM - 04:00 AM (+1)");
    expect(
      weeklyOpeningHours({ ...berlin, openingStart: "01:00", openingEnd: "05:00" }, "America/New_York", "2026-10-07")[0]
        ?.hours,
    ).toBe("07:00 PM (-1) - 11:00 PM (-1)");
  });

  it("keeps a closing midnight on its own day", () => {
    const evenings = { ...berlin, openingStart: "18:00", openingEnd: "24:00" };
    expect(weeklyOpeningHours(evenings, "Europe/Berlin", "2026-10-07")[0]?.hours).toBe("06:00 PM - 12:00 AM");
    expect(weeklyOpeningHours(evenings, "America/New_York", "2026-10-07")[0]?.hours).toBe("12:00 PM - 06:00 PM");
  });

  it("reads open around the clock the same in every timezone", () => {
    const allDay = { ...berlin, openingStart: "00:00", openingEnd: "24:00" };
    expect(hours(weeklyOpeningHours(allDay, "Pacific/Auckland", "2026-10-07"))).toEqual(
      Array(7).fill("12:00 AM - 12:00 AM"),
    );
  });

  it("converts each day of the selected week with that day's clock offsets", () => {
    // Berlin leaves summer time on Sunday 25 October 2026.
    const week = hours(weeklyOpeningHours(berlin, "UTC", "2026-10-21"));
    expect(week.slice(0, 6)).toEqual(Array(6).fill("06:00 AM - 03:00 PM"));
    expect(week[6]).toBe("07:00 AM - 04:00 PM");
  });

  it("reports closed days and a day's own hours", () => {
    const schedule = {
      ...berlin,
      openDays: [1, 2, 3, 4, 5],
      openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "14:00" }],
    };
    const week = weeklyOpeningHours(schedule, "Europe/Berlin", "2026-10-07");
    expect(week[1]).toEqual({ dayOfWeek: 2, hours: "10:00 AM - 02:00 PM" });
    expect(week[0]).toEqual({ dayOfWeek: 1, hours: "08:00 AM - 05:00 PM" });
    expect(week.slice(5).map((day) => day.hours)).toEqual([null, null]);
  });
});
