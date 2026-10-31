import { beforeAll as beforeBookingCatalog } from "vitest";
import appI18n from "@/modules/common/i18n";

beforeBookingCatalog(async () => {
  appI18n.addResourceBundle(
    "en-US",
    "booking",
    (await import("@/modules/common/i18n/locales/en-US/booking.json")).default,
  );
});

import { describe, expect, it } from "vitest";
import { weeklyOpeningHours } from "@/modules/booking/configuration/openingHoursFacts";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";

const berlin = {
  timezone: "Europe/Berlin",
  openingHours: [...ALL_ISO_WEEKDAYS].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "17:00" })),
};
const hours = (days: ReturnType<typeof weeklyOpeningHours>) => days.map((day) => day.hours);
const allDay = {
  ...berlin,
  openingHours: berlin.openingHours.map((entry) => ({ ...entry, start: "00:00", end: "24:00" })),
};

describe("weeklyOpeningHours", () => {
  it("converts each weekday into the viewer's timezone", () => {
    expect(hours(weeklyOpeningHours(berlin, "America/New_York", "2026-10-07"))).toEqual(
      Array(7).fill("02:00 AM - 11:00 AM"),
    );
  });

  it("marks a boundary on the next or previous date with (+1) or (-1)", () => {
    expect(weeklyOpeningHours(berlin, "Pacific/Auckland", "2026-10-07")[0]?.hours).toBe("07:00 PM - 04:00 AM (+1)");
    expect(
      weeklyOpeningHours(
        { ...berlin, openingHours: berlin.openingHours.map((entry) => ({ ...entry, start: "01:00", end: "05:00" })) },
        "America/New_York",
        "2026-10-07",
      )[0]?.hours,
    ).toBe("07:00 PM (-1) - 11:00 PM (-1)");
  });

  it("keeps a closing midnight on its own day", () => {
    const evenings = {
      ...berlin,
      openingHours: berlin.openingHours.map((entry) => ({ ...entry, start: "18:00", end: "24:00" })),
    };
    expect(weeklyOpeningHours(evenings, "Europe/Berlin", "2026-10-07")[0]?.hours).toBe("06:00 PM - 12:00 AM");
    expect(weeklyOpeningHours(evenings, "America/New_York", "2026-10-07")[0]?.hours).toBe("12:00 PM - 06:00 PM");
  });

  it("reads open around the clock the same in every timezone", () => {
    expect(hours(weeklyOpeningHours(allDay, "Pacific/Auckland", "2026-10-07"))).toEqual(
      Array(7).fill("12:00 AM - 12:00 AM"),
    );
  });

  it("labels whole-day openings only where they stay whole local days", () => {
    expect(hours(weeklyOpeningHours(allDay, "Pacific/Auckland", "2026-10-07", undefined, "Open all day"))).toEqual(
      Array(7).fill("Open all day"),
    );
    const mondayAllDay = {
      ...berlin,
      openingHours: berlin.openingHours.map((entry) =>
        entry.dayOfWeek === 1 ? { ...entry, start: "00:00", end: "24:00" } : entry,
      ),
    };
    expect(weeklyOpeningHours(mondayAllDay, "Europe/Berlin", "2026-10-07", undefined, "Open all day")[0]?.hours).toBe(
      "Open all day",
    );
    expect(
      weeklyOpeningHours(mondayAllDay, "America/New_York", "2026-10-07", undefined, "Open all day")[0]?.hours,
    ).toBe("06:00 PM (-1) - 06:00 PM");
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
      openingHours: berlin.openingHours
        .filter((entry) => entry.dayOfWeek <= 5)
        .map((entry) => (entry.dayOfWeek === 2 ? { ...entry, start: "10:00", end: "14:00" } : entry)),
    };
    const week = weeklyOpeningHours(schedule, "Europe/Berlin", "2026-10-07");
    expect(week[1]).toEqual({ dayOfWeek: 2, hours: "10:00 AM - 02:00 PM" });
    expect(week[0]).toEqual({ dayOfWeek: 1, hours: "08:00 AM - 05:00 PM" });
    expect(week.slice(5).map((day) => day.hours)).toEqual([null, null]);
  });
});

it.each([
  { locale: "en-US-u-hc-h12", monday: "09:00 AM - 05:00 PM", tuesday: "10:00 AM - 12:00 AM" },
  { locale: "en-GB-u-hc-h23", monday: "09:00 - 17:00", tuesday: "10:00 - 00:00" },
])("formats each independent day with $locale without changing its wire end", ({ locale, monday, tuesday }) => {
  const schedule = {
    timezone: "UTC",
    openingHours: [
      { dayOfWeek: 1, start: "09:00", end: "17:00" },
      { dayOfWeek: 2, start: "10:00", end: "24:00" },
    ],
  };
  expect(hours(weeklyOpeningHours(schedule, "UTC", "2026-10-07", locale))).toEqual([
    monday,
    tuesday,
    null,
    null,
    null,
    null,
    null,
  ]);
  expect(schedule.openingHours[1]?.end).toBe("24:00");
});
