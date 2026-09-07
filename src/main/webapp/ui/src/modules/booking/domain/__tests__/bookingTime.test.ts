import { describe, expect, test } from "vitest";
import {
  addCalendarDays,
  broadUtcEnvelope,
  currentWallClock,
  dayMinuteToZonedTime,
  displayInterval,
  formatAgendaPeriod,
  instantToWallClockMinute,
  isPlainDate,
  resolveWallClock,
  sliceAcrossZonedDay,
  wallClockDraftFromInstants,
  wallClockInstant,
  wallClockToDayMinute,
  zonedDayBounds,
} from "../bookingTime";

describe("bookingTime", () => {
  test("validates and adds calendar dates", () => {
    expect(isPlainDate("2026-02-28")).toBe(true);
    expect(isPlainDate("2026-02-30")).toBe(false);
    expect(addCalendarDays("2026-02-28", 1)).toBe("2026-03-01");
  });

  test.each([
    ["2026-02-10", 1440, "2026-02-09T23:00:00Z", "2026-02-10T23:00:00Z"],
    ["2026-03-29", 1380, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"],
    ["2026-10-25", 1500, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"],
  ])("uses DST-correct Berlin bounds for %s", (date, minutes, start, end) => {
    expect(zonedDayBounds(date, "Europe/Berlin")).toEqual({ start, end, elapsedMinutes: minutes });
  });

  test("resolves consecutive midnights independently when the first midnight is skipped", () => {
    expect(zonedDayBounds("2026-09-06", "America/Santiago")).toEqual({
      start: "2026-09-06T04:00:00Z",
      end: "2026-09-07T03:00:00Z",
      elapsedMinutes: 1380,
    });
  });

  test("computes one envelope for several row timezones", () => {
    expect(broadUtcEnvelope("2026-03-29", ["Europe/Berlin", "America/New_York"])).toEqual({
      start: "2026-03-28T23:00:00Z",
      end: "2026-03-30T04:00:00Z",
    });
  });

  test.each([
    ["2026-03-29", 1380, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"],
    ["2026-10-25", 1500, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"],
  ])("creates a DST-correct absolute full-day display interval for %s", (date, elapsedMinutes, start, end) => {
    expect(displayInterval(date, "Europe/Berlin", "00:00", "24:00")).toEqual({
      date,
      timeZone: "Europe/Berlin",
      start,
      end,
      elapsedMinutes,
    });
  });

  test.each(["02:45", "03:00"])("collapses a preferred window inside the spring gap ending at %s", (end) => {
    expect(displayInterval("2026-03-29", "Europe/Berlin", "02:30", end)).toMatchObject({
      start: "2026-03-29T01:00:00Z",
      end: "2026-03-29T01:00:00Z",
      elapsedMinutes: 0,
    });
  });

  test("keeps wall-clock formatting separate from elapsed timeline positions", () => {
    expect(instantToWallClockMinute("2026-03-29T01:30:00Z", "2026-03-29", "Europe/Berlin")).toBe(210);
    expect(sliceAcrossZonedDay("2026-03-28T22:30:00Z", "2026-03-29T23:30:00Z", "2026-03-29", "Europe/Berlin")).toEqual({
      startMinute: -30,
      endMinute: 1470,
    });
  });

  test("preserves both repeated-hour occurrences and the final hour of a long day", () => {
    const date = "2026-10-25";
    const timezone = "Europe/Berlin";
    expect(sliceAcrossZonedDay("2026-10-25T00:45:00Z", "2026-10-25T01:15:00Z", date, timezone)).toEqual({
      startMinute: 165,
      endMinute: 195,
    });
    expect(wallClockToDayMinute(date, timezone, 24 * 60)).toBe(1500);
    const start = dayMinuteToZonedTime(date, timezone, 165).toInstant().toString();
    const end = dayMinuteToZonedTime(date, timezone, 195).toInstant().toString();
    expect(wallClockDraftFromInstants(start, end, timezone)).toEqual({
      startDate: date,
      startTime: "02:45",
      startOccurrence: "earlier",
      endDate: date,
      endTime: "02:15",
      endOccurrence: "later",
    });
    expect(dayMinuteToZonedTime(date, timezone, 1470).toPlainTime().toString()).toBe("23:30:00");
  });

  test("gets the local date and current minute from an injected instant", () => {
    expect(currentWallClock("2026-08-17T22:30:00Z", "Europe/Berlin")).toEqual({
      date: "2026-08-18",
      minute: 30,
    });
  });

  test("clamps a skipped-hour display boundary to the next real instant", () => {
    expect(wallClockToDayMinute("2026-03-29", "Europe/Berlin", 150)).toBe(120);
    expect(wallClockToDayMinute("2026-03-29", "Europe/Berlin", 195)).toBe(135);
  });

  test("distinguishes unique, ambiguous, and nonexistent wall-clock times", () => {
    expect(resolveWallClock("2026-02-10", "09:30", "Europe/Berlin")).toEqual({
      kind: "unique",
      instant: "2026-02-10T08:30:00Z",
    });
    expect(resolveWallClock("2026-10-25", "02:30", "Europe/Berlin")).toEqual({
      kind: "ambiguous",
      earlier: "2026-10-25T00:30:00Z",
      later: "2026-10-25T01:30:00Z",
    });
    expect(resolveWallClock("2026-03-29", "02:30", "Europe/Berlin")).toEqual({
      kind: "nonexistent",
    });
  });

  test("round-trips the selected fall-back occurrence without changing its instant", () => {
    const draft = wallClockDraftFromInstants("2026-10-25T01:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin");

    expect(draft).toMatchObject({
      startDate: "2026-10-25",
      startTime: "02:30",
      startOccurrence: "later",
      endDate: "2026-10-25",
      endTime: "03:30",
    });
    expect(
      wallClockInstant(resolveWallClock(draft.startDate, draft.startTime, "Europe/Berlin"), draft.startOccurrence),
    ).toBe("2026-10-25T01:30:00Z");
  });

  test("shows a shared timezone only once in an agenda period", () => {
    const period = formatAgendaPeriod("2026-08-18T08:00:00Z", "2026-08-18T09:00:00Z", "Europe/Berlin", "en-GB");

    expect(period.match(/GMT\+2/g)).toHaveLength(1);
  });
});
