import { describe, expect, test } from "vitest";
import { incrementFitsSchedule } from "@/modules/booking/domain/bookingIncrement";
import {
  ALL_ISO_WEEKDAYS,
  customOpeningHours,
  formatIsoWeekday,
  isOpenAllDay,
  longestOpeningMinutes,
  MAX_BOOKING_DURATION_MINUTES,
  mapOpeningHours,
  type OpeningSchedule,
  validOpeningHours,
} from "../bookingOpeningHours";

function schedule(overrides: Partial<OpeningSchedule> = {}): OpeningSchedule {
  return {
    openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

    ...overrides,
  };
}

describe("opening-hour schemas", () => {
  test.each<[string, number[]]>([
    ["empty", []],
    ["more than seven days", [1, 2, 3, 4, 5, 6, 7, 1]],
    ["duplicate days", [1, 1]],
    ["day zero", [0]],
    ["day eight", [8]],
    ["a fractional day", [1.5]],
  ])("rejects a schedule with %s", (_, days) => {
    const entries = days.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" }));
    expect(() => mapOpeningHours(entries)).toThrow();
  });

  test("accepts independent intervals and sorts weekdays without changing the input", () => {
    const entries = [
      { dayOfWeek: 3, start: "10:00", end: "24:00" },
      { dayOfWeek: 1, start: "09:00", end: "17:00" },
    ];
    expect(mapOpeningHours(entries)).toEqual([entries[1], entries[0]]);
    expect(entries.map((entry) => entry.dayOfWeek)).toEqual([3, 1]);
  });

  test.each([
    ["a reversed interval", "16:00", "10:00", false],
    ["an empty interval", "10:00", "10:00", false],
    ["24:00 after a non-midnight start", "08:00", "24:00", true],
    ["24:00 after the last minute", "23:59", "24:00", true],
    ["the full-day interval", "00:00", "24:00", true],
    ["24:00 as the start", "24:00", "24:00", false],
    ["00:00 as the end after a later start", "08:00", "00:00", false],
    ["missing zero padding", "8:00", "18:00", false],
    ["seconds", "08:00:00", "18:00", false],
  ])("checks weekday intervals: %s", (_, start, end, valid) => {
    expect(validOpeningHours(String(start), String(end))).toBe(valid);
  });
});

describe("opening-hour editor helpers", () => {
  test("remembers only valid custom intervals", () => {
    const custom = { start: "08:00", end: "24:00" };
    expect(customOpeningHours(custom)).toEqual(custom);
    expect(customOpeningHours({ start: "00:00", end: "24:00" })).toBeUndefined();
    expect(customOpeningHours({ start: "18:00", end: "08:00" })).toBeUndefined();
    expect(customOpeningHours({ start: "", end: "18:00" })).toBeUndefined();
    expect(isOpenAllDay(custom)).toBe(false);
    expect(isOpenAllDay({ start: "00:00", end: "24:00" })).toBe(true);
  });

  test("formats ISO weekdays in the requested locale", () => {
    expect(formatIsoWeekday(1)).toBe("Monday");
    expect(formatIsoWeekday(7, "long", "de-DE")).toBe("Sonntag");
  });
});

describe("duration increments and continuous opening periods", () => {
  test.each([
    [[1], "09:00", "17:00", 480],
    [[7, 1], "00:00", "24:00", 2880],
    [[1, 3], "00:00", "24:00", 1440],
    [[1, 2, 3, 4, 5, 6, 7], "00:00", "24:00", MAX_BOOKING_DURATION_MINUTES],
  ])("merges the weekly schedule %s", (days, start, end, expected) => {
    expect(
      longestOpeningMinutes(schedule({ openingHours: days.map((dayOfWeek) => ({ dayOfWeek, start, end })) })),
    ).toBe(expected);
  });
  test("merges evening, full-day and morning intervals across Sunday", () => {
    const hours = schedule({
      openingHours: [
        { dayOfWeek: 6, start: "18:00", end: "24:00" },
        { dayOfWeek: 7, start: "00:00", end: "24:00" },
        { dayOfWeek: 1, start: "00:00", end: "06:00" },
      ],
    });
    expect(longestOpeningMinutes(hours)).toBe(2160);
    const increment = (slotGranularityMinutes: number) =>
      incrementFitsSchedule({ ...hours, slotGranularityMinutes, incrementUnit: "MINUTES" });
    expect(increment(2160)).toBe(true);
    expect(increment(2161)).toBe(false);
  });
});
