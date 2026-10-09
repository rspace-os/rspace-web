import { describe, expect, test } from "vitest";
import i18n from "@/modules/common/i18n";
import { resolveBookingWindow } from "../../creation/ZonedBookingWindowFields";
import {
  addCalendarDays,
  bookingDateTimeLocale,
  bookingDateTimeLocaleFor,
  bookingHourCycle,
  bookingLocale,
  broadUtcEnvelope,
  currentWallClock,
  dayMinuteToZonedTime,
  displayInterval,
  formatAgendaPeriod,
  formatBookingDateTime,
  formatBookingPeriod,
  formatDurationMinutes,
  formatPlainDate,
  formatWallClockTime,
  instantToWallClockMinute,
  isPlainDate,
  resolveWallClock,
  sameTimeZone,
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

  test("names the date in a booking period, even when two bookings share the times", () => {
    const period = formatBookingPeriod("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z", "Europe/Berlin", "en-US");

    expect(period).toContain("Oct 8, 2026");
    expect(period).not.toBe(
      formatBookingPeriod("2026-10-09T04:00:00Z", "2026-10-09T05:00:00Z", "Europe/Berlin", "en-US"),
    );
  });

  describe("dated times on a clock-change day", () => {
    // Europe/Berlin repeats 02:00-03:00 on 2026-10-25 and skips it on 2026-03-29.
    const dateTime = (instant: string) =>
      new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
        timeStyle: "short",
        hourCycle: bookingHourCycle("AUTOMATIC"),
        timeZone: "Europe/Berlin",
      }).format(new Date(instant));

    test("tells the two occurrences of a repeated hour apart by their offsets", () => {
      const earlier = formatBookingDateTime("2026-10-25T00:30:00Z", "Europe/Berlin", "en-US");
      const later = formatBookingDateTime("2026-10-25T01:30:00Z", "Europe/Berlin", "en-US");

      expect(earlier).toBe(`${dateTime("2026-10-25T00:30:00Z")} +02:00`);
      expect(later).toBe(`${dateTime("2026-10-25T01:30:00Z")} +01:00`);
      expect(dateTime("2026-10-25T00:30:00Z")).toBe(dateTime("2026-10-25T01:30:00Z"));
      expect(formatBookingPeriod("2026-10-25T00:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${earlier} – ${dateTime("2026-10-25T02:30:00Z")} +01:00`,
      );
      expect(formatBookingPeriod("2026-10-25T01:30:00Z", "2026-10-25T02:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${later} – ${dateTime("2026-10-25T02:30:00Z")} +01:00`,
      );
    });

    test("names the offsets either side of a skipped hour", () => {
      expect(formatBookingPeriod("2026-03-29T00:30:00Z", "2026-03-29T01:30:00Z", "Europe/Berlin", "en-US")).toBe(
        `${dateTime("2026-03-29T00:30:00Z")} +01:00 – ${dateTime("2026-03-29T01:30:00Z")} +02:00`,
      );
    });

    test("leaves an ordinary day's times without offsets", () => {
      expect(formatBookingDateTime("2026-10-08T04:00:00Z", "Europe/Berlin", "en-US")).toBe(
        dateTime("2026-10-08T04:00:00Z"),
      );
      expect(formatBookingPeriod("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z", "Europe/Berlin", "en-US")).toBe(
        new Intl.DateTimeFormat("en-US", {
          dateStyle: "medium",
          timeStyle: "short",
          hourCycle: bookingHourCycle("AUTOMATIC"),
          timeZone: "Europe/Berlin",
        }).formatRange(new Date("2026-10-08T04:00:00Z"), new Date("2026-10-08T05:00:00Z")),
      );
    });
  });

  test("formats wall-clock times and dates the way the locale's native inputs show them", () => {
    expect(formatWallClockTime("14:05", "en-US")).toBe("02:05 PM");
    expect(formatWallClockTime("00:00", "en-US")).toBe("12:00 AM");
    expect(formatWallClockTime("24:00", "en-US")).toBe("12:00 AM");
    expect(formatWallClockTime("14:05", "en-GB")).toBe("14:05");
    expect(formatPlainDate("2026-08-17", "en-US")).toBe("08/17/2026");
    expect(formatPlainDate("2026-08-17", "de-DE")).toBe("17.08.2026");
  });

  test("spells out durations in the largest whole units", () => {
    expect(formatDurationMinutes(120, "en-US")).toBe("2 hours");
    expect(formatDurationMinutes(1470, "en-US")).toBe("1 day, 30 minutes");
    expect(formatDurationMinutes(1, "en-US")).toBe("1 minute");
    expect(formatDurationMinutes(0, "en-US")).toBe("0 minutes");
  });

  test("words follow the app language, numeric dates and times follow the browser region", () => {
    // Tests run i18next in cimode, which is not a locale Intl can format.
    expect(bookingLocale()).toBe("en-US");
    const resolvedLanguage = i18n.resolvedLanguage;
    i18n.resolvedLanguage = "en-GB";
    try {
      expect(bookingLocale()).toBe("en-GB");
      // The app language does not change the numeric format, which native inputs take from the browser.
      expect(formatWallClockTime("14:05")).toBe(formatWallClockTime("14:05", bookingDateTimeLocale()));
    } finally {
      i18n.resolvedLanguage = resolvedLanguage;
    }
    expect(formatWallClockTime("14:05", "en-GB")).toBe("14:05");
    expect(formatAgendaPeriod("2026-08-18T13:00:00Z", "2026-08-18T14:00:00Z", "UTC", "en-GB")).toMatch(
      /^13:00\s?–\s?14:00/,
    );
  });

  test("an explicit Time format replaces the browser region's clock but not its date order", () => {
    const regional = bookingDateTimeLocaleFor("AUTOMATIC");
    expect(bookingHourCycle("H24")).toBe("h23");
    expect(formatWallClockTime("14:05", bookingDateTimeLocale("H24"))).toBe("14:05");
    expect(formatWallClockTime("00:00", bookingDateTimeLocale("H24"))).toBe("00:00");
    expect(formatBookingDateTime("2026-10-08T12:05:00Z", "UTC", "en-US", "H24")).toBe("Oct 8, 2026, 12:05");
    expect(formatPlainDate("2026-08-17")).toBe(formatPlainDate("2026-08-17", regional));

    expect(bookingHourCycle("H12")).toBe("h12");
    expect(new Intl.Locale(bookingDateTimeLocale("H12")).hourCycle).toBe("h12");
    expect(formatBookingDateTime("2026-10-08T12:05:00Z", "UTC", "en-US", "H12").replace(/\s/g, " ")).toBe(
      "Oct 8, 2026, 12:05 PM",
    );
    expect(bookingDateTimeLocale()).toBe(regional);
  });

  test("drafts selected timeline ranges as a forward booking window", () => {
    for (const [date, timezone] of [
      ["2026-03-29", "Europe/Berlin"],
      ["2026-10-25", "Europe/Berlin"],
      ["2026-02-10", "UTC"],
    ] as const) {
      const minutes = zonedDayBounds(date, timezone).elapsedMinutes;
      for (let start = 0; start < minutes; start += 5) {
        const end = Math.min(minutes, start + 60);
        if (end <= start) continue;
        const draft = wallClockDraftFromInstants(
          dayMinuteToZonedTime(date, timezone, start).toInstant().toString(),
          dayMinuteToZonedTime(date, timezone, end).toInstant().toString(),
          timezone,
        );
        expect(resolveBookingWindow(draft, timezone).orderInvalid, `${date} ${timezone} ${start}-${end}`).toBe(false);
      }
    }
  });
});

describe("sameTimeZone", () => {
  test("treats aliases of one zone as the same zone", () => {
    expect(sameTimeZone("Etc/UTC", "UTC")).toBe(true);
    expect(sameTimeZone("Asia/Kolkata", "Asia/Calcutta")).toBe(true);
    expect(sameTimeZone("Europe/Berlin", "Europe/Paris")).toBe(false);
  });
});
