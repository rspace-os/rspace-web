import * as v from "valibot";
import { describe, expect, test } from "vitest";
import {
  customOpeningHours,
  formatIsoWeekday,
  isOpenAllDay,
  OpeningHoursEntrySchema,
  OpeningHoursSchema,
} from "../bookingOpeningHours";

describe("opening-hour schemas", () => {
  test.each<[string, unknown[]]>([
    ["empty", []],
    ["more than seven days", [1, 2, 3, 4, 5, 6, 7, 1]],
    ["duplicate days", [1, 1]],
    ["day zero", [0]],
    ["day eight", [8]],
    ["a fractional day", [1.5]],
    ["a string day", ["1"]],
  ])("rejects a schedule with %s", (_, days) => {
    const entries = days.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" }));
    expect(v.safeParse(OpeningHoursSchema, entries).success).toBe(false);
  });

  test("accepts independent intervals and sorts weekdays without changing the input", () => {
    const entries = [
      { dayOfWeek: 3, start: "10:00", end: "24:00" },
      { dayOfWeek: 1, start: "09:00", end: "17:00" },
    ];
    expect(v.parse(OpeningHoursSchema, entries)).toEqual([entries[1], entries[0]]);
    expect(entries.map((entry) => entry.dayOfWeek)).toEqual([3, 1]);
  });

  test.each([
    { input: null },
    { input: [1] },
    { input: [null] },
    { input: [[{ dayOfWeek: 1 }]] },
    { input: [{ dayOfWeek: 1, start: "09:00" }] },
    { input: [{ dayOfWeek: 1, start: "09:00", end: "17:00", hidden: true }] },
  ])("rejects malformed rows $input", ({ input }) => {
    expect(v.safeParse(OpeningHoursSchema, input).success).toBe(false);
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
    ["a non-string boundary", 8, "18:00", false],
  ])("checks weekday intervals: %s", (_, start, end, valid) => {
    expect(v.safeParse(OpeningHoursEntrySchema, { dayOfWeek: 2, start, end }).success).toBe(valid);
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
