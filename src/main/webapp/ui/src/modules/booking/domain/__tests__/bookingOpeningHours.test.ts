import { Temporal } from "@js-temporal/polyfill";
import * as v from "valibot";
import { describe, expect, test } from "vitest";
import {
  ALL_ISO_WEEKDAYS,
  coversInterval,
  effectiveHours,
  ISO_WEEKDAYS,
  MAX_BOOKING_DURATION_MINUTES,
  OpenDaysSchema,
  OpeningExceptionsSchema,
  type OpeningSchedule,
  openingIntervals,
  resolveSchedulingBoundary,
  validOpeningExceptions,
} from "../bookingOpeningHours";

function schedule(overrides: Partial<OpeningSchedule> = {}): OpeningSchedule {
  return {
    openingStart: "00:00",
    openingEnd: "24:00",
    openDays: ALL_ISO_WEEKDAYS,
    openingExceptions: [],
    ...overrides,
  };
}

// 2026-06-01 is a Monday.
const officeHours = schedule({
  openingStart: "09:00",
  openingEnd: "17:00",
  openingExceptions: [{ dayOfWeek: ISO_WEEKDAYS.TUESDAY, start: "10:00", end: "16:00" }],
});

describe("opening-hour schemas", () => {
  test.each([
    ["empty", []],
    ["more than seven days", [1, 2, 3, 4, 5, 6, 7, 1]],
    ["duplicate days", [1, 1]],
    ["day zero", [0]],
    ["day eight", [8]],
    ["a fractional day", [1.5]],
    ["a string day", ["1"]],
  ])("rejects open days with %s", (_, openDays) => {
    expect(v.safeParse(OpenDaysSchema, openDays).success).toBe(false);
  });

  test("accepts one to seven unique ISO weekdays in any order", () => {
    expect(v.safeParse(OpenDaysSchema, [3]).success).toBe(true);
    expect(v.safeParse(OpenDaysSchema, [7, 1, 2, 3, 4, 5, 6]).success).toBe(true);
  });

  test("rejects duplicate exception days and out-of-range exception days", () => {
    expect(
      v.safeParse(OpeningExceptionsSchema, [
        { dayOfWeek: 2, start: "10:00", end: "16:00" },
        { dayOfWeek: 2, start: "11:00", end: "15:00" },
      ]).success,
    ).toBe(false);
    expect(v.safeParse(OpeningExceptionsSchema, [{ dayOfWeek: 8, start: "10:00", end: "16:00" }]).success).toBe(false);
    expect(v.safeParse(OpeningExceptionsSchema, [{ dayOfWeek: 2, start: "10:00" }]).success).toBe(false);
    expect(v.safeParse(OpeningExceptionsSchema, []).success).toBe(true);
  });

  test("rejects an exception on a closed day", () => {
    expect(
      validOpeningExceptions({ openDays: [1, 3], openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "16:00" }] }),
    ).toBe(false);
    expect(
      validOpeningExceptions({ openDays: [1, 2], openingExceptions: [{ dayOfWeek: 2, start: "10:00", end: "16:00" }] }),
    ).toBe(true);
  });

  test.each([
    ["a reversed interval", "16:00", "10:00", false],
    ["an empty interval", "10:00", "10:00", false],
    ["24:00 after a non-midnight start", "08:00", "24:00", true],
    ["24:00 after the last minute of the day", "23:59", "24:00", true],
    ["the full-day interval", "00:00", "24:00", true],
    ["24:00 as the start", "24:00", "24:00", false],
    ["00:00 as the end after a later start", "08:00", "00:00", false],
  ])("checks exception intervals: %s", (_, start, end, valid) => {
    expect(validOpeningExceptions({ openDays: [2], openingExceptions: [{ dayOfWeek: 2, start, end }] })).toBe(valid);
  });
});

describe("effectiveHours", () => {
  test("resolves closed days, exceptions, and shared hours", () => {
    const settings = { ...officeHours, openDays: [1, 2, 3, 4, 5] };
    expect(effectiveHours(settings, ISO_WEEKDAYS.MONDAY)).toEqual({ start: "09:00", end: "17:00" });
    expect(effectiveHours(settings, ISO_WEEKDAYS.TUESDAY)).toEqual({ start: "10:00", end: "16:00" });
    expect(effectiveHours(settings, ISO_WEEKDAYS.SUNDAY)).toBeNull();
  });
});

describe("resolveSchedulingBoundary", () => {
  test("shifts a nonexistent time forward by the gap", () => {
    expect(resolveSchedulingBoundary("2026-03-29", "02:15", "Europe/Berlin").toString()).toBe("2026-03-29T01:15:00Z");
  });

  test("picks the earlier offset for a repeated time", () => {
    expect(resolveSchedulingBoundary("2026-10-25", "02:30", "Europe/Berlin").toString()).toBe("2026-10-25T00:30:00Z");
  });

  test("resolves 24:00 as the next calendar date's start", () => {
    expect(resolveSchedulingBoundary("2026-03-28", "24:00", "Europe/Berlin").toString()).toBe("2026-03-28T23:00:00Z");
  });
});

describe("coversInterval", () => {
  test("applies the Tuesday exception but the shared hours on Monday", () => {
    expect(coversInterval(officeHours, "UTC", { start: "2026-06-01T09:30:00Z", end: "2026-06-01T10:30:00Z" })).toBe(
      true,
    );
    expect(coversInterval(officeHours, "UTC", { start: "2026-06-02T09:30:00Z", end: "2026-06-02T10:30:00Z" })).toBe(
      false,
    );
    expect(coversInterval(officeHours, "UTC", { start: "2026-06-02T10:00:00Z", end: "2026-06-02T16:00:00Z" })).toBe(
      true,
    );
    expect(coversInterval(officeHours, "UTC", { start: "2026-06-02T15:30:00Z", end: "2026-06-02T16:30:00Z" })).toBe(
      false,
    );
  });

  test("rejects closed days and empty intervals", () => {
    const weekdays = { ...officeHours, openDays: [1, 2, 3, 4, 5] };
    expect(coversInterval(weekdays, "UTC", { start: "2026-06-07T09:30:00Z", end: "2026-06-07T10:30:00Z" })).toBe(false);
    expect(coversInterval(weekdays, "UTC", { start: "2026-06-01T09:30:00Z", end: "2026-06-01T09:30:00Z" })).toBe(false);
  });

  test("lets an all-day Monday booking end at Tuesday midnight but not a minute later", () => {
    const mondays = schedule({ openDays: [ISO_WEEKDAYS.MONDAY] });
    expect(coversInterval(mondays, "UTC", { start: "2026-06-01T00:00:00Z", end: "2026-06-02T00:00:00Z" })).toBe(true);
    expect(coversInterval(mondays, "UTC", { start: "2026-06-01T00:00:00Z", end: "2026-06-02T00:01:00Z" })).toBe(false);
  });

  test("spans consecutive all-day open days but not a closed middle day", () => {
    const interval = { start: "2026-06-01T12:00:00Z", end: "2026-06-03T12:00:00Z" };
    expect(coversInterval(schedule({ openDays: [1, 2, 3] }), "UTC", interval)).toBe(true);
    expect(coversInterval(schedule({ openDays: [1, 3] }), "UTC", interval)).toBe(false);
  });

  test("does not let partial daily hours cover the overnight closure", () => {
    expect(coversInterval(officeHours, "UTC", { start: "2026-06-01T16:00:00Z", end: "2026-06-02T11:00:00Z" })).toBe(
      false,
    );
  });

  test("uses the instrument weekday when the display date is a different weekday", () => {
    const mondays = schedule({ openDays: [ISO_WEEKDAYS.MONDAY] });
    // Monday 00:00 at UTC+14 is Sunday 10:00 UTC.
    const interval = { start: "2026-05-31T10:00:00Z", end: "2026-05-31T11:00:00Z" };
    expect(Temporal.Instant.from(interval.start).toZonedDateTimeISO("UTC").dayOfWeek).toBe(ISO_WEEKDAYS.SUNDAY);
    expect(coversInterval(mondays, "Pacific/Kiritimati", interval)).toBe(true);
    expect(coversInterval(mondays, "UTC", interval)).toBe(false);
  });

  test("uses the instrument weekday at a large negative offset", () => {
    const mondays = schedule({ openDays: [ISO_WEEKDAYS.MONDAY] });
    // Monday 00:00 at UTC-12 is Monday 12:00 UTC.
    expect(coversInterval(mondays, "Etc/GMT+12", { start: "2026-06-01T11:00:00Z", end: "2026-06-01T12:00:00Z" })).toBe(
      false,
    );
    expect(coversInterval(mondays, "Etc/GMT+12", { start: "2026-06-01T12:00:00Z", end: "2026-06-01T13:00:00Z" })).toBe(
      true,
    );
  });

  test("rejects a booking wholly inside a DST gap", () => {
    const gapHours = schedule({ openingStart: "02:00", openingEnd: "03:00" });
    expect(
      coversInterval(gapHours, "Europe/Berlin", { start: "2026-03-29T00:30:00Z", end: "2026-03-29T01:30:00Z" }),
    ).toBe(false);
  });
});

describe("openingIntervals", () => {
  test("returns every window a display date intersects", () => {
    // Los Angeles 2026-06-01 (PDT) is 07:00Z to 07:00Z next day; Tokyo 09:00–17:00 is 00:00Z–08:00Z,
    // so the view shows the end of Tokyo Monday and the start of Tokyo Tuesday.
    const losAngelesDay = { start: "2026-06-01T07:00:00Z", end: "2026-06-02T07:00:00Z" };
    const tokyo = schedule({ openingStart: "09:00", openingEnd: "17:00" });
    expect(openingIntervals(tokyo, "Asia/Tokyo", losAngelesDay)).toEqual([
      { start: "2026-06-01T07:00:00Z", end: "2026-06-01T08:00:00Z" },
      { start: "2026-06-02T00:00:00Z", end: "2026-06-02T07:00:00Z" },
    ]);
    expect(openingIntervals({ ...tokyo, openDays: [1, 3, 4, 5, 6, 7] }, "Asia/Tokyo", losAngelesDay)).toEqual([
      { start: "2026-06-01T07:00:00Z", end: "2026-06-01T08:00:00Z" },
    ]);
  });

  test("merges adjacent all-day windows and skips closed days", () => {
    expect(
      openingIntervals(schedule({ openDays: [1, 2, 4] }), "UTC", {
        start: "2026-06-01T00:00:00Z",
        end: "2026-06-05T00:00:00Z",
      }),
    ).toEqual([
      { start: "2026-06-01T00:00:00Z", end: "2026-06-03T00:00:00Z" },
      { start: "2026-06-04T00:00:00Z", end: "2026-06-05T00:00:00Z" },
    ]);
  });

  test("resolves Berlin 02:15–02:45 on the spring-forward day to 03:15–03:45", () => {
    const [opening, ...rest] = openingIntervals(
      schedule({ openingStart: "02:15", openingEnd: "02:45" }),
      "Europe/Berlin",
      { start: "2026-03-28T23:00:00Z", end: "2026-03-29T22:00:00Z" },
    );
    expect(rest).toEqual([]);
    expect(opening).toEqual({ start: "2026-03-29T01:15:00Z", end: "2026-03-29T01:45:00Z" });
    expect(Temporal.Instant.from(opening.end).since(opening.start).total("minutes")).toBe(30);
    expect(Temporal.Instant.from(opening.start).toZonedDateTimeISO("Europe/Berlin").toPlainTime().toString()).toBe(
      "03:15:00",
    );
  });

  test.each([
    ["the 23-hour spring-forward day", "2026-03-29", "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z", 1380],
    ["the 25-hour fall-back day", "2026-10-25", "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z", 1500],
  ])("covers %s", (_, date, start, end, minutes) => {
    const sundays = schedule({ openDays: [ISO_WEEKDAYS.SUNDAY] });
    const week = {
      start: Temporal.Instant.from(start).subtract({ hours: 72 }).toString(),
      end: Temporal.Instant.from(end).add({ hours: 72 }).toString(),
    };
    expect(Temporal.PlainDate.from(date).dayOfWeek).toBe(ISO_WEEKDAYS.SUNDAY);
    const openings = openingIntervals(sundays, "Europe/Berlin", week);
    expect(openings).toEqual([{ start, end }]);
    expect(Temporal.Instant.from(end).since(start).total("minutes")).toBe(minutes);
  });

  test("drops a window wholly inside a DST gap", () => {
    expect(
      openingIntervals(schedule({ openingStart: "02:00", openingEnd: "03:00" }), "Europe/Berlin", {
        start: "2026-03-28T23:00:00Z",
        end: "2026-03-29T22:00:00Z",
      }),
    ).toEqual([]);
  });

  test("starts an ambiguous boundary at the earlier offset", () => {
    expect(
      openingIntervals(schedule({ openingStart: "02:30", openingEnd: "17:00" }), "Europe/Berlin", {
        start: "2026-10-24T22:00:00Z",
        end: "2026-10-25T23:00:00Z",
      }),
    ).toEqual([{ start: "2026-10-25T00:30:00Z", end: "2026-10-25T16:00:00Z" }]);
  });

  test("enumerates exactly the absolute limit and rejects one minute more", () => {
    const start = Temporal.Instant.from("2026-01-01T00:00:00Z");
    const end = start.add({ minutes: MAX_BOOKING_DURATION_MINUTES });
    expect(openingIntervals(schedule(), "UTC", { start: start.toString(), end: end.toString() })).toEqual([
      { start: start.toString(), end: end.toString() },
    ]);
    expect(() =>
      openingIntervals(schedule(), "UTC", { start: start.toString(), end: end.add({ minutes: 1 }).toString() }),
    ).toThrow(RangeError);
    expect(() =>
      coversInterval(schedule(), "UTC", { start: start.toString(), end: end.add({ minutes: 1 }).toString() }),
    ).toThrow(RangeError);
  });

  test("rejects a reversed interval and returns nothing for an empty one", () => {
    expect(() =>
      openingIntervals(schedule(), "UTC", { start: "2026-06-02T00:00:00Z", end: "2026-06-01T00:00:00Z" }),
    ).toThrow(RangeError);
    expect(openingIntervals(schedule(), "UTC", { start: "2026-06-01T00:00:00Z", end: "2026-06-01T00:00:00Z" })).toEqual(
      [],
    );
  });
});
